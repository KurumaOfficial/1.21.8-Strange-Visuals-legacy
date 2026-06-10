package ru.strange.client.module.impl.world;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.strange.client.event.EventInit;
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

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@IModule(
        name = "FireFly",
        description = "Светлячки вокруг игрока",
        category = Category.World,
        bind = -1
)
public class FireFly extends Module {

    private static final int BUFFER_SIZE = 1 << 16;
    private static final Identifier FIRE_TEXTURE = Identifier.of("strange", "textures/world/firefly.png");
    private static final long PARTICLE_FADE_OUT_MS = 420L;

    private final SliderSetting count = new SliderSetting("Количество", 80.0f, 20.0f, 220.0f, 1.0f, false);
    private final SliderSetting range = new SliderSetting("Радиус", 24.0f, 8.0f, 50.0f, 1.0f, false);
    private final SliderSetting size = new SliderSetting("Размер", 0.12f, 0.04f, 0.34f, 0.01f, false);
    private final SliderSetting speed = new SliderSetting("Скорость", 1.0f, 0.25f, 3.0f, 0.05f, false);
    private final SliderSetting alpha = new SliderSetting("Альфа", 180.0f, 35.0f, 255.0f, 5.0f, false);
    private final SliderSetting trailLength = new SliderSetting("Длина шлейфа", 16.0f, 4.0f, 28.0f, 1.0f, false);
    private final SliderSetting trailLife = new SliderSetting("Время шлейфа", 1500.0f, 500.0f, 2600.0f, 50.0f, false);
    private final BooleanSetting rainbow = new BooleanSetting("Радуга", false);
    private final BooleanSetting shaderColors = new BooleanSetting("Shader Colors", true);
    private final ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.SOLARIS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !shaderColors.get());
    private final HueSetting color = new HueSetting("Цвет", new Color(255, 210, 115));

    private final List<FireParticle> particles = new ArrayList<>();
    private final BufferAllocator allocator = new BufferAllocator(BUFFER_SIZE);
    private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

    private float toggleProgress = 0.0f;
    private boolean disabling = false;
    private long lastToggleNanos = System.nanoTime();

    public FireFly() {
        addSettings(count, range, size, speed, alpha, trailLength, trailLife, rainbow, shaderColors, shaderTheme, color);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        disabling = false;
        lastToggleNanos = System.nanoTime();
    }

    @Override
    public void onDisable() {
        disabling = true;
        lastToggleNanos = System.nanoTime();
        // Keep event handlers registered to allow smooth fade-out;
        // they will be unregistered automatically once particles fully fade.
    }

    private void finalizeDisable() {
        ru.strange.client.event.EventManager.unregister(this);
    }

    private float updateToggleProgress() {
        long now = System.nanoTime();
        float dt = Math.min(0.25f, (now - lastToggleNanos) / 1_000_000_000f);
        lastToggleNanos = now;
        float target = (enable && !disabling) ? 1.0f : 0.0f;
        // Ease toward target with critical-damped style spring; ~0.6s full transition.
        float k = 1.0f - (float) Math.exp(-dt / 0.18f);
        toggleProgress += (target - toggleProgress) * k;
        if (Math.abs(toggleProgress - target) < 0.0015f) toggleProgress = target;
        if (!enable && toggleProgress <= 0.005f) {
            particles.clear();
            if (disabling) {
                disabling = false;
                finalizeDisable();
            }
        }
        return toggleProgress;
    }

    private static float easeInOutCubic(float t) {
        if (t < 0.5f) return 4.0f * t * t * t;
        float u = -2.0f * t + 2.0f;
        return 1.0f - u * u * u * 0.5f;
    }

    public boolean shouldKeepRendering() {
        return enable || toggleProgress > 0.005f;
    }

    @EventInit
    public void onMotion(EventMotion event) {
        if (mc.player == null || mc.world == null) {
            particles.clear();
            return;
        }

        Vec3d center = mc.gameRenderer != null && mc.gameRenderer.getCamera() != null
                ? mc.gameRenderer.getCamera().getPos()
                : mc.player.getPos();

        int targetCount = (enable && !disabling) ? Math.max(0, (int) count.get()) : 0;
        while (particles.size() < targetCount) {
            particles.add(new FireParticle(center, range.get()));
        }
    }

    @EventInit
    public void onRender(EventRender3D event) {
        float fade = updateToggleProgress();
        if (mc.player == null || mc.world == null || particles.isEmpty() || fade <= 0.005f) {
            return;
        }

        long now = System.currentTimeMillis();
        float speedScale = speed.get();
        float areaRange = range.get();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        Vec3d forward = mc.player.getRotationVec(event.getTickDelta());
        Vec3d viewCenter = cameraPos.add(forward.multiply(Math.min(11.0, areaRange * 0.32)));

        MatrixStack matrices = event.getMatrixStack();

        try {
            Iterator<FireParticle> iterator = particles.iterator();
            while (iterator.hasNext()) {
                FireParticle particle = iterator.next();
                particle.update(now, speedScale, areaRange, viewCenter, (int) trailLength.get());
                if (particle.dead) {
                    iterator.remove();
                }
            }

            VertexConsumer buffer = immediate.getBuffer(FIRE_LAYER_DEPTH);
            for (FireParticle particle : particles) {
                renderParticle(matrices, buffer, cameraPos, particle, now);
            }

            immediate.draw();
        } finally {
            allocator.clear();
        }
    }

    private void renderParticle(MatrixStack matrices, VertexConsumer buffer, Vec3d cameraPos, FireParticle particle, long now) {
        float lifePc = particle.lifeProgress(now);
        if (lifePc <= 0.0f) {
            return;
        }

        float fadeFactor = lifePc > 0.7f ? (1.0f - (lifePc - 0.7f) / 0.3f) : 1.0f;
        fadeFactor *= easeInOutCubic(Math.max(0f, Math.min(1f, toggleProgress)));

        int rgb = resolveColor(now, particle.seed, particle.x, particle.y, particle.z);

        float mainPulse = 0.82f + 0.28f * (float) Math.sin(now * 0.0056 + particle.seed * 8.0);
        float mainSize = size.get() * mainPulse;
        int mainAlpha = clampAlpha((int) (alpha.get() * lifePc * fadeFactor * fadeFactor));

        drawSprite(
                matrices,
                buffer,
                (float) (particle.x - cameraPos.x),
                (float) (particle.y - cameraPos.y),
                (float) (particle.z - cameraPos.z),
                mainSize,
                rgb,
                mainAlpha,
                0.0f
        );

        // Small orbiting sparks around each firefly to mimic the original lively effect.
        float orbitRadius = 0.085f + (float) (Math.sin(now * 0.0017 + particle.seed * 12.0) * 0.02f);
        for (int i = 0; i < 2; i++) {
            double phase = now * 0.0028 + particle.seed * 10.0 + i * Math.PI;
            float ox = (float) Math.cos(phase) * orbitRadius;
            float oz = (float) Math.sin(phase) * orbitRadius;
            float oy = (float) Math.sin(phase * 1.5) * 0.032f;

            drawSprite(
                    matrices,
                    buffer,
                    (float) (particle.x - cameraPos.x) + ox,
                    (float) (particle.y - cameraPos.y) + oy,
                    (float) (particle.z - cameraPos.z) + oz,
                    mainSize * 0.45f * fadeFactor,
                    rgb,
                    clampAlpha((int) (mainAlpha * 0.58f)),
                    (float) Math.toDegrees(phase)
            );
        }

        renderTrail(matrices, buffer, cameraPos, particle, rgb, now);
    }

    private void renderTrail(MatrixStack matrices, VertexConsumer buffer, Vec3d cameraPos, FireParticle particle, int rgb, long now) {
        if (particle.trail.isEmpty()) {
            return;
        }

        int total = particle.trail.size();
        int index = 0;
        float lifeMs = Math.max(1.0f, trailLife.get());

        for (TrailPoint point : particle.trail) {
            float age = (now - point.time) / lifeMs;
            if (age >= 1.0f) {
                index++;
                continue;
            }

            float headFactor = total <= 1 ? 1.0f : index / (float) (total - 1);
            float fade = (1.0f - age);
            fade = fade * fade;
            float alphaFactor = fade * (0.35f + 0.65f * headFactor);
            int trailAlphaValue = clampAlpha((int) (alpha.get() * (0.20f + 0.80f * alphaFactor)));
            float trailSize = size.get() * (0.40f + headFactor * 0.56f);

            drawSprite(
                    matrices,
                    buffer,
                    (float) (point.x - cameraPos.x),
                    (float) (point.y - cameraPos.y),
                    (float) (point.z - cameraPos.z),
                    trailSize,
                    rgb,
                    trailAlphaValue,
                    0.0f
            );
            index++;
        }

        long trimBefore = now - (long) lifeMs;
        while (!particle.trail.isEmpty() && particle.trail.peekFirst().time < trimBefore) {
            particle.trail.removeFirst();
        }
    }

    private void drawSprite(MatrixStack matrices, VertexConsumer buffer,
                            float x, float y, float z,
                            float drawSize,
                            int rgb, int alphaValue,
                            float spinDeg) {
        if (alphaValue <= 0 || drawSize <= 0.001f) {
            return;
        }

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
        if (spinDeg != 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinDeg));
        }
        matrices.scale(drawSize, drawSize, 1.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f)
                .color(r, g, b, alphaValue)
                .texture(0.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f)
                .color(r, g, b, alphaValue)
                .texture(1.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f)
                .color(r, g, b, alphaValue)
                .texture(1.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f)
                .color(r, g, b, alphaValue)
                .texture(0.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);

        matrices.pop();
    }

    private int resolveColor(long now, double seed, double x, double y, double z) {
        if (shaderColors.get()) {
            return ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), now * 0.0014 + seed * 4.0 + x * 0.34 + y * 0.19 + z * 0.21);
        }
        if (rainbow.get()) {
            float hue = (float) ((now * 0.0002 + seed * 0.35) % 1.0);
            return Color.HSBtoRGB(hue, 0.88f, 1.0f);
        }
        return color.getRGB();
    }

    private int clampAlpha(int alphaValue) {
        return Math.max(0, Math.min(255, alphaValue));
    }

    private static final class FireParticle {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        final double seed;
        long birthTime;
        long lifeTime;
        long lastUpdate;
        long lastTrailMark;
        boolean dead;
        boolean fadingOut;
        long fadeOutStart;
        final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();

        FireParticle(Vec3d center, float range) {
            this.seed = ThreadLocalRandom.current().nextDouble(0.0, 1.0);
            respawn(center, range, System.currentTimeMillis());
        }

        void respawn(Vec3d center, float range, long now) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double angle = random.nextDouble(0.0, Math.PI * 2.0);
            double radius = random.nextDouble(1.5, Math.max(2.5, range));
            this.x = center.x + Math.cos(angle) * radius;
            this.z = center.z + Math.sin(angle) * radius;
            this.y = center.y + random.nextDouble(-4.0, 6.5);
            this.vx = random.nextDouble(-0.018, 0.018);
            this.vy = random.nextDouble(-0.008, 0.012);
            this.vz = random.nextDouble(-0.018, 0.018);
            this.birthTime = now;
            this.lifeTime = random.nextLong(5600L, 11600L);
            this.lastUpdate = now;
            this.lastTrailMark = now;
            this.dead = false;
            this.fadingOut = false;
            this.fadeOutStart = 0L;
            this.trail.clear();
            this.trail.add(new TrailPoint(this.x, this.y, this.z, now));
        }

        void update(long now, float speedScale, float range, Vec3d centerPos, int trailLimit) {
            if (dead) {
                return;
            }

            if (fadingOut) {
                vx *= 0.94;
                vy *= 0.94;
                vz *= 0.94;
                x += vx * speedScale;
                y += vy * speedScale;
                z += vz * speedScale;

                if (now - lastTrailMark >= 26L) {
                    lastTrailMark = now;
                    trail.addLast(new TrailPoint(x, y, z, now));
                    while (trail.size() > Math.max(2, trailLimit)) {
                        trail.removeFirst();
                    }
                }

                if (now - fadeOutStart >= PARTICLE_FADE_OUT_MS) {
                    respawn(centerPos, range, now);
                }
                return;
            }

            long dtMs = Math.max(1L, now - lastUpdate);
            lastUpdate = now;
            double dt = Math.min(0.07, dtMs / 1000.0) * speedScale;

            double wander = now * 0.0022 + seed * 12.3;
            vx += Math.cos(wander) * 0.0022 * dt;
            vz += Math.sin(wander * 1.2) * 0.0022 * dt;
            vy += Math.sin(wander * 0.8) * 0.0012 * dt;

            double orbitAngle = wander * 0.42 + seed * 9.0;
            double orbitRadius = Math.max(4.0, range * 0.72);
            double targetX = centerPos.x + Math.cos(orbitAngle) * orbitRadius;
            double targetZ = centerPos.z + Math.sin(orbitAngle) * orbitRadius;
            double targetY = centerPos.y + Math.sin(wander * 0.9 + seed * 2.0) * 3.4;

            vx += (targetX - x) * 0.00095 * dt;
            vz += (targetZ - z) * 0.00095 * dt;
            vy += (targetY - y) * 0.00062 * dt;

            vx *= 0.985;
            vy *= 0.984;
            vz *= 0.985;

            x += vx * (dt * 60.0);
            y += vy * (dt * 60.0);
            z += vz * (dt * 60.0);

            double minY = centerPos.y - Math.max(4.0, range * 0.30);
            double maxY = centerPos.y + Math.max(6.0, range * 0.45);
            if (y < minY) {
                y = minY;
                vy = Math.abs(vy) * 0.7;
            }
            if (y > maxY) {
                y = maxY;
                vy = -Math.abs(vy) * 0.7;
            }

            double dx = x - centerPos.x;
            double dy = y - centerPos.y;
            double dz = z - centerPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            double maxDist = range + 8.0;
            if (distSq > maxDist * maxDist) {
                beginFadeOut(now);
                return;
            }

            if (now - birthTime > lifeTime) {
                beginFadeOut(now);
                return;
            }

            if (now - lastTrailMark >= 20L) {
                lastTrailMark = now;
                trail.addLast(new TrailPoint(x, y, z, now));
                while (trail.size() > Math.max(2, trailLimit)) {
                    trail.removeFirst();
                }
            }
        }

        float lifeProgress(long now) {
            if (fadingOut) {
                float fade = 1.0f - Math.min(1.0f, (now - fadeOutStart) / (float) PARTICLE_FADE_OUT_MS);
                return fade * fade;
            }

            long age = Math.max(0L, now - birthTime);
            if (lifeTime <= 0L) {
                return 0.0f;
            }

            float t = Math.min(1.0f, age / (float) lifeTime);
            float fadeIn = Math.min(1.0f, t / 0.18f);
            float fadeOut = Math.max(0.0f, 1.0f - t);
            return fadeIn * fadeOut * fadeOut;
        }

        private void beginFadeOut(long now) {
            if (fadingOut) {
                return;
            }

            fadingOut = true;
            fadeOutStart = now;
        }
    }

    private static final class TrailPoint {
        final double x;
        final double y;
        final double z;
        final long time;

        TrailPoint(double x, double y, double z, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }
    }

    private static final RenderPipeline FIRE_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "firefly_depth"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer FIRE_LAYER_DEPTH = RenderLayer.of(
            "strange_firefly_depth",
            BUFFER_SIZE,
            false,
            true,
            FIRE_DEPTH_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(new RenderPhase.Texture(FIRE_TEXTURE, false))
                    .build(false)
    );
}
