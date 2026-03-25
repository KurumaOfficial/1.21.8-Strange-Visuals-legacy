package ru.strange.client.module.impl.player;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventMotion;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.math.Mathf;
import ru.strange.client.utils.particle.ParticleUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@IModule(
        name = "Частицы Игрока",
        description = "Частицы при атаке и броске",
        category = Category.Player,
        bind = -1
)
public class PlayerParticles extends Module {
    public static BooleanSetting attackEnabled = new BooleanSetting("Атака", true);
    public static BooleanSetting throwEnabled = new BooleanSetting("Бросок", true);
    public static ModeSetting particleStyle = new ModeSetting("Стиль", "Custom", "Custom", "Theme");
    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(131, 166, 232))
            .hidden(() -> particleStyle.is("Theme"));
    public static ModeSetting particleMode = new ModeSetting("Тип частиц", "Bloom", "Bloom", "Star", "Snow", "Heart", "Dollar", "Triangle", "Sakura", "Genshin", "Rhombus")
            .hidden(() -> particleStyle.is("Theme"));
    public static ModeSetting shaderTheme = new ModeSetting("Theme Preset", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !particleStyle.is("Theme"));
    public static SliderSetting size = new SliderSetting("Размер", 0.5f, 0.0f, 1.0f, 0.1f, false);

    private final List<ParticleUtil.Particle> targetParticles = new ArrayList<>();
    private final List<ParticleUtil.Particle> flameParticles = new ArrayList<>();
    private long lastUpdateTime = System.nanoTime();

    public PlayerParticles() {
        addSettings(attackEnabled, throwEnabled, particleStyle, colorSetting, particleMode, shaderTheme, size);
    }

    private void clear() {
        targetParticles.clear();
        flameParticles.clear();
    }

    private void migrateLegacyShaderMode() {
        if (!ShaderThemeVisuals.isShaderMode(particleMode.get())) {
            return;
        }

        particleMode.currentMode = "Bloom";
        particleStyle.currentMode = "Theme";
    }

    private void spawnParticle(List<ParticleUtil.Particle> particles, Vec3d position, Vec3d velocity) {
        migrateLegacyShaderMode();

        float particleSize = 0.05F + (this.size.get() * 0.2F);
        boolean themed = particleStyle.is("Theme");
        double phase = particles.size() * 0.37 + position.x * 0.19 + position.y * 0.27 + position.z * 0.13;
        int color = themed ? ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), phase) : colorSetting.getRGB();
        ParticleUtil.ParticleType type = themed ? ShaderThemeVisuals.particleType(shaderTheme.get()) : resolveParticleType();

        particles.add(new ParticleUtil.Particle(
                mc,
                type,
                position.add(0.0, particleSize, 0.0),
                velocity,
                particles.size(),
                (int) Mathf.step(Mathf.randomValue(0, 360), 15),
                color,
                particleSize,
                0.2F
        ));
    }

    private ParticleUtil.ParticleType resolveParticleType() {
        return switch (particleMode.get()) {
            case "Heart" -> ParticleUtil.ParticleType.HEART;
            case "Star" -> ParticleUtil.ParticleType.STAR;
            case "Snow" -> ParticleUtil.ParticleType.SNOW;
            case "Dollar" -> ParticleUtil.ParticleType.DOLLAR;
            case "Triangle" -> ParticleUtil.ParticleType.TRIANGLE;
            case "Sakura" -> ParticleUtil.ParticleType.SAKURA;
            case "Genshin" -> ParticleUtil.ParticleType.GEMINI;
            case "Rhombus" -> ParticleUtil.ParticleType.SIMS;
            default -> ParticleUtil.ParticleType.BLOOM;
        };
    }

    @EventInit
    public void onAttack(EventAttack event) {
        if (!attackEnabled.get() || !event.isConfirmed() || event.getTarget() == null) {
            return;
        }

        Entity target = event.getTarget();
        float motion = 6.0f;
        for (int i = 0; i < 35; i++) {
            spawnParticle(
                    targetParticles,
                    new Vec3d(target.getX(), target.getY() + Mathf.randomValue(0, target.getHeight()), target.getZ()),
                    new Vec3d(Mathf.randomValue(-motion, motion), Mathf.randomValue(-motion, motion), Mathf.randomValue(-motion, motion))
            );
        }
    }

    @EventInit
    public void onMotion(EventMotion event) {
        if (!throwEnabled.get() || mc.world == null) {
            return;
        }

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity || entity instanceof ArrowEntity || entity instanceof TridentEntity)) {
                continue;
            }

            if (entity instanceof TridentEntity trident && trident.isOnGround()) {
                continue;
            }

            boolean moving = entity.lastX != entity.getX() || entity.lastY != entity.getY() || entity.lastZ != entity.getZ();
            if (!moving) {
                continue;
            }

            Vec3d pos = entity.getPos();
            for (int i = 0; i < 4; i++) {
                spawnParticle(
                        flameParticles,
                        new Vec3d(
                                pos.x + MathHelper.nextDouble(Random.create(), -0.2, 0.2),
                                pos.y + MathHelper.nextDouble(Random.create(), -0.2, 0.2),
                                pos.z + MathHelper.nextDouble(Random.create(), -0.2, 0.2)
                        ),
                        new Vec3d(
                                MathHelper.nextDouble(Random.create(), -1.0, 1.0),
                                MathHelper.nextDouble(Random.create(), -0.3, 0.3),
                                MathHelper.nextDouble(Random.create(), -1.0, 1.0)
                        )
                );
            }
        }

        removeExpiredParticles(targetParticles, 2000);
        removeExpiredParticles(flameParticles, 2000);
    }

    @EventInit
    public void onRender(EventRender3D event) {
        MatrixStack matrix = event.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        long now = System.nanoTime();
        double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
        lastUpdateTime = now;

        BufferAllocator allocator = new BufferAllocator(1 << 18);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        try {
            ParticleUtil.renderParticles(matrix, immediate, cameraPos, targetParticles, 400, 600, deltaTime);
            ParticleUtil.renderParticles(matrix, immediate, cameraPos, flameParticles, 700, 1200, deltaTime);
            immediate.draw();
        } finally {
            allocator.close();
        }
    }

    private void removeExpiredParticles(List<ParticleUtil.Particle> particles, long lifespan) {
        particles.removeIf(particle -> particle.time().finished(lifespan));
    }

    @Override
    public void toggle() {
        super.toggle();
        clear();
    }

    @EventInit
    public void onChangeWorld(EventChangeWorld event) {
        clear();
    }
}
