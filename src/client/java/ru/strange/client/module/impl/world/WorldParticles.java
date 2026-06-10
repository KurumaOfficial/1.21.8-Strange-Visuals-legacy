package ru.strange.client.module.impl.world;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventMotion;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
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
        name = "Частицы Мира",
        description = "Частицы в мире",
        category = Category.World,
        bind = -1
)
public class WorldParticles extends Module {
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;
    private static final long PARTICLE_LIFESPAN_MS = 4000L;
    public static ModeSetting particleStyle = new ModeSetting("Стиль", "Custom", "Custom", "Theme");
    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(131, 166, 232))
            .hidden(() -> particleStyle.is("Theme"));
    public static ModeSetting particleMode = new ModeSetting("Тип частиц", "Bloom", "Bloom", "Star", "Snow", "Heart", "Dollar", "Triangle", "Sakura", "Genshin", "Rhombus")
            .hidden(() -> particleStyle.is("Theme"));
    public static ModeSetting shaderTheme = new ModeSetting("Theme Preset", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !particleStyle.is("Theme"));
    public static SliderSetting size = new SliderSetting("Размер", 0.5f, 0.0f, 1.0f, 0.1f, false);

    private final List<ParticleUtil.Particle> worldParticles = new ArrayList<>();
    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);
    private long lastUpdateTime = System.nanoTime();

    public WorldParticles() {
        addSettings(particleStyle, colorSetting, particleMode, shaderTheme, size);
    }

    private void resetState() {
        worldParticles.clear();
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
        double phase = particles.size() * 0.23 + position.x * 0.17 + position.y * 0.31 + position.z * 0.11;
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
    public void onMotion(EventMotion event) {
        if (mc.world == null || mc.player == null) {
            resetState();
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

        ParticleUtil.updateParticles(worldParticles, 1500, 2200, deltaTime);

        int radius = 12;
        int spawnAttempts = Math.max(1, Math.round((Optimization.shouldLimitWorldEffects() ? 4 : 6) * Optimization.getParticleMultiplier()));
        for (int i = 0; i < spawnAttempts; i++) {
            Vec3d additional = mc.player.getPos().add(
                    Mathf.randomValue(-radius, radius),
                    0.0,
                    Mathf.randomValue(-radius, radius)
            );

            BlockPos topPos = mc.world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING,
                    BlockPos.ofFloored(additional)
            );

            double x = topPos.getX() + Mathf.randomValue(0, 1);
            double z = topPos.getZ() + Mathf.randomValue(0, 1);
            double y = mc.player.getY() + Mathf.randomValue(mc.player.getHeight(), radius);

            Vec3d spawnPos = new Vec3d(x, y, z);
            while (!mc.world.isAir(BlockPos.ofFloored(spawnPos)) && spawnPos.y < mc.world.getTopYInclusive()) {
                spawnPos = spawnPos.add(0.0, 1.0, 0.0);
            }

            spawnParticle(
                    worldParticles,
                    spawnPos,
                    new Vec3d(
                        mc.player.getVelocity().x + Mathf.randomValue(-1.2f, 1.2f),
                        Mathf.randomValue(-0.12f, 0.12F),
                        mc.player.getVelocity().z + Mathf.randomValue(-1.2f, 1.2f)
                    )
            );
        }

        removeExpiredParticles(worldParticles, PARTICLE_LIFESPAN_MS);
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
            ParticleUtil.renderParticles(matrix, renderVertexConsumers, cameraPos, worldParticles, event.getTickDelta());
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
