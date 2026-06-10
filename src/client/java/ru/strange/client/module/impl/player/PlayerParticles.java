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
import ru.strange.client.module.impl.other.Optimization;
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
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;
    private static final long PARTICLE_LIFESPAN_MS = 2000L;
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
    private final Random random = Random.create();
    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);
    private long lastUpdateTime = System.nanoTime();

    public PlayerParticles() {
        addSettings(attackEnabled, throwEnabled, particleStyle, colorSetting, particleMode, shaderTheme, size);
    }

    private void resetState() {
        targetParticles.clear();
        flameParticles.clear();
        lastUpdateTime = System.nanoTime();
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

    private double randomBetween(double min, double max) {
        return MathHelper.nextDouble(random, min, max);
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
        if (!attackEnabled.get() || !event.isConfirmed() || event.getTarget() == null || Optimization.shouldDisableModuleParticles()) {
            return;
        }

        float mul = Optimization.getParticleMultiplier();
        if (mul <= 0f) return;

        Entity target = event.getTarget();
        float motion = 6.0f;
        int count = Math.max(1, Math.round(35 * mul));
        for (int i = 0; i < count; i++) {
            spawnParticle(
                    targetParticles,
                    new Vec3d(target.getX(), target.getY() + Mathf.randomValue(0, target.getHeight()), target.getZ()),
                    new Vec3d(Mathf.randomValue(-motion, motion), Mathf.randomValue(-motion, motion), Mathf.randomValue(-motion, motion))
            );
        }
    }

    @EventInit
    public void onMotion(EventMotion event) {
        if (mc.world == null) {
            return;
        }

        if (Optimization.shouldDisableModuleParticles()) {
            resetState();
            return;
        }

        float particleMul = Optimization.getParticleMultiplier();
        if (particleMul <= 0f) {
            resetState();
            return;
        }

        long now = System.nanoTime();
        double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
        lastUpdateTime = now;

        ParticleUtil.updateParticles(targetParticles, 400, 600, deltaTime);
        ParticleUtil.updateParticles(flameParticles, 700, 1200, deltaTime);

        if (!throwEnabled.get()) {
            removeExpiredParticles(targetParticles, PARTICLE_LIFESPAN_MS);
            removeExpiredParticles(flameParticles, PARTICLE_LIFESPAN_MS);
            return;
        }

        double searchRadius = Math.max(32.0, mc.options.getViewDistance().getValue() * 16.0);
        net.minecraft.util.math.Box searchBounds = mc.player.getBoundingBox().expand(searchRadius);
        for (Entity entity : mc.world.getOtherEntities(mc.player, searchBounds)) {
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
            int throwCount = Math.max(1, Math.round(4 * particleMul));
            for (int i = 0; i < throwCount; i++) {
                spawnParticle(
                        flameParticles,
                        new Vec3d(
                                pos.x + randomBetween(-0.2, 0.2),
                                pos.y + randomBetween(-0.2, 0.2),
                                pos.z + randomBetween(-0.2, 0.2)
                        ),
                        new Vec3d(
                                randomBetween(-1.0, 1.0),
                                randomBetween(-0.3, 0.3),
                                randomBetween(-1.0, 1.0)
                        )
                );
            }
        }

        removeExpiredParticles(targetParticles, PARTICLE_LIFESPAN_MS);
        removeExpiredParticles(flameParticles, PARTICLE_LIFESPAN_MS);
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }

        if (Optimization.shouldDisableModuleParticles()) {
            resetState();
            return;
        }

        MatrixStack matrix = event.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        try {
            ParticleUtil.renderParticles(matrix, renderVertexConsumers, cameraPos, targetParticles, event.getTickDelta());
            ParticleUtil.renderParticles(matrix, renderVertexConsumers, cameraPos, flameParticles, event.getTickDelta());
            renderVertexConsumers.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    private void removeExpiredParticles(List<ParticleUtil.Particle> particles, long lifespan) {
        particles.removeIf(particle -> particle.time().finished(lifespan));
    }

    @Override
    public void toggle() {
        super.toggle();
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    @EventInit
    public void onChangeWorld(EventChangeWorld event) {
        resetState();
    }
}
