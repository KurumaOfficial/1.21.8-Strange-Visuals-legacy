package ru.strange.client.module.impl.world;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.strange.client.event.EventInit;
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

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;

@IModule(
        name = "Призрак",
        description = "Оставляет след из хитбоксов при движении",
        category = Category.World,
        bind = -1
)
public class Ghost extends Module {

    private static final int BUFFER_SIZE = 1 << 18;

    private final SliderSetting trail = new SliderSetting("След", 1.0f, 1.0f, 10.0f, 1.0f, false);
    private final SliderSetting fade = new SliderSetting("Время", 950.0f, 300.0f, 3000.0f, 50.0f, false);
        private final ModeSetting renderMode = new ModeSetting("Режим", "Боксы", "Боксы", "Player");
        private final BooleanSetting shaderFill = new BooleanSetting("Shader Fill", false).hidden(() -> !renderMode.is("Боксы"));
        private final ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !renderMode.is("Боксы") || !shaderFill.get());
    private final HueSetting color = new HueSetting("Цвет", new Color(80, 255, 120));
    private final BooleanSetting rainbow = new BooleanSetting("Радуга", false);
    private final BooleanSetting onlyMoving = new BooleanSetting("Только в движении", true);

    private final BufferAllocator allocator = new BufferAllocator(BUFFER_SIZE);
    private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

    private final List<GhostBox> ghosts = new ArrayList<>();
    private long lastSpawnTime;
    private Vec3d lastSpawnPos;

    public Ghost() {
        addSettings(trail, fade, renderMode, shaderFill, shaderTheme, color, rainbow, onlyMoving);
    }

    @Override
    public void onDisable() {
        ghosts.clear();
        lastSpawnTime = 0L;
        lastSpawnPos = null;
        super.onDisable();
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;

        updateGhosts();

        if (ghosts.isEmpty()) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = event.getMatrixStack();
        float tickDelta = event.getTickDelta();
        float time = (mc.player.age + tickDelta) * 0.05f;

        try {
            Iterator<GhostBox> it = ghosts.iterator();
            while (it.hasNext()) {
                GhostBox ghost = it.next();
                float progress = ghost.getProgress();

                if (progress >= 1.0f) {
                    it.remove();
                    continue;
                }

                renderGhost(matrices, cameraPos, ghost, progress, time);
            }

            immediate.draw();
        } finally {
            allocator.clear();
        }
    }

    private void updateGhosts() {
        if (mc.player == null) return;

        ghosts.removeIf(g -> g.getProgress() >= 1.0f);

        if (onlyMoving.get()) {
            Vec3d vel = mc.player.getVelocity();
            double horizontal = vel.x * vel.x + vel.z * vel.z;
            if (horizontal < 0.0025) return;
        }

        long now = System.currentTimeMillis();
        Vec3d currentPos = mc.player.getPos();

        float density = trail.get();
        long minDelay = getSpawnDelay(density);
        double minDistance = getSpawnDistance(density);

        if (now - lastSpawnTime < minDelay) return;
        if (lastSpawnPos != null && currentPos.distanceTo(lastSpawnPos) < minDistance) return;

        spawnGhost(mc.player);
        lastSpawnTime = now;
        lastSpawnPos = currentPos;
    }

    private long getSpawnDelay(float density) {
        float t = (density - 1.0f) / 9.0f;
        return (long) MathHelper.lerp(t, 520.0f, 180.0f);
    }

    private double getSpawnDistance(float density) {
        float t = (density - 1.0f) / 9.0f;
        return MathHelper.lerp(t, 1.15, 0.28);
    }

    private void spawnGhost(PlayerEntity player) {
        Box bb = player.getBoundingBox();

        ghosts.add(new GhostBox(
                bb.minX, bb.minY, bb.minZ,
                bb.maxX, bb.maxY, bb.maxZ,
                (long) fade.get()
        ));
    }

    private void renderGhost(MatrixStack matrices, Vec3d cameraPos, GhostBox ghost, float progress, float time) {
        float alphaCurve = alphaCurve(progress);
        float scale = appearCurve(progress);

        int rgb = getColor(time, ghost);

        Box bb = ghost.getBox();

        double cx = (bb.minX + bb.maxX) * 0.5;
        double cy = (bb.minY + bb.maxY) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;

        double hx = (bb.maxX - bb.minX) * 0.5 * scale;
        double hy = (bb.maxY - bb.minY) * scale;
        double hz = (bb.maxZ - bb.minZ) * 0.5 * scale;

        Box scaled = new Box(
                cx - hx, bb.minY, cz - hz,
                cx + hx, bb.minY + hy, cz + hz
        );

        if (isCameraInside(cameraPos, scaled) || isTooCloseToCamera(cameraPos, cx, cy, cz)) {
            return;
        }

        if (renderMode.is("Player")) {
            renderGhostPlayer(matrices, ghost, scaled, cameraPos, rgb, alphaCurve);
            return;
        }

        int fillA = (int) (78.0f * alphaCurve);
        int lineA = (int) (165.0f * alphaCurve);
        int glowA = (int) (90.0f * alphaCurve);

        Box renderBox = scaled.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        if (fillA > 0) {
            VertexConsumer fillBuffer = immediate.getBuffer(FILL_LAYER);
            drawFilledBox(fillBuffer, matrix, renderBox, rgb, fillA);
        }

        if (lineA > 0) {
            VertexConsumer lineBuffer = immediate.getBuffer(LINE_LAYER_2);
            drawBoxOutline(lineBuffer, matrix, renderBox, rgb, lineA);
        }

        if (glowA > 0) {
            VertexConsumer glowBuffer = immediate.getBuffer(GLOW_LAYER);
            drawBoxOutline(glowBuffer, matrix, renderBox.expand(0.02), mixWhite(rgb, 0.45f), glowA);
        }
    }

    private boolean isCameraInside(Vec3d cameraPos, Box box) {
        return cameraPos.x >= box.minX && cameraPos.x <= box.maxX
                && cameraPos.y >= box.minY && cameraPos.y <= box.maxY
                && cameraPos.z >= box.minZ && cameraPos.z <= box.maxZ;
    }

    private boolean isTooCloseToCamera(Vec3d cameraPos, double x, double y, double z) {
        double dx = cameraPos.x - x;
        double dy = cameraPos.y - y;
        double dz = cameraPos.z - z;
        return dx * dx + dy * dy + dz * dz < 1.4 * 1.4;
    }

    private int getColor(float time, GhostBox ghost) {
        if (shaderFill.get() && renderMode.is("Боксы")) {
            return ShaderThemeVisuals.animatedPrimary(
                    shaderTheme.get(),
                    time * 0.85 + (ghost.minX + ghost.minZ) * 0.42
            );
        }
        if (rainbow.get()) {
            float hue = (time * 0.18f + (float) ((ghost.minX + ghost.minZ) * 0.08f)) % 1.0f;
            return Color.HSBtoRGB(hue, 0.85f, 1.0f);
        }
        return color.getRGB();
    }

    private void renderGhostPlayer(MatrixStack matrices, GhostBox ghost, Box scaled, Vec3d cameraPos, int color, float alphaCurve) {
        if (!(mc.player instanceof AbstractClientPlayerEntity clientPlayer)) {
            return;
        }

        int alpha = (int) (210.0f * alphaCurve);
        if (alpha <= 4) {
            return;
        }

        double centerX = (scaled.minX + scaled.maxX) * 0.5 - cameraPos.x;
        double baseY = scaled.minY - cameraPos.y;
        double centerZ = (scaled.minZ + scaled.maxZ) * 0.5 - cameraPos.z;
        float width = (float) ((scaled.maxX - scaled.minX) * 1.22f);
        float height = (float) ((scaled.maxY - scaled.minY) * 1.06f);

        matrices.push();
        matrices.translate(centerX, baseY, centerZ);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
        matrices.scale(width, height, 1.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer skinBuffer = immediate.getBuffer(RenderLayer.getEntityTranslucent(clientPlayer.getSkinTextures().texture()));

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        skinBuffer.vertex(matrix, -0.5f, 1.0f, 0.0f)
                .color(r, g, b, alpha)
                .texture(0.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
        skinBuffer.vertex(matrix, 0.5f, 1.0f, 0.0f)
                .color(r, g, b, alpha)
                .texture(1.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
        skinBuffer.vertex(matrix, 0.5f, 0.0f, 0.0f)
                .color(r, g, b, alpha)
                .texture(1.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
        skinBuffer.vertex(matrix, -0.5f, 0.0f, 0.0f)
                .color(r, g, b, alpha)
                .texture(0.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);

        matrices.pop();
    }

    private float alphaCurve(float progress) {
        float fadeIn = Math.min(progress / 0.18f, 1.0f);
        float fadeOut = 1.0f - progress;
        fadeOut = fadeOut * fadeOut;
        return smoothStep(fadeIn) * fadeOut;
    }

    private float appearCurve(float progress) {
        float t = Math.min(progress / 0.20f, 1.0f);
        return 0.88f + 0.12f * easeOutExpo(t);
    }

    private void drawFilledBox(VertexConsumer buffer, Matrix4f matrix, Box bb, int color, int alpha) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = alpha / 255.0f;

        float x1 = (float) bb.minX;
        float y1 = (float) bb.minY;
        float z1 = (float) bb.minZ;
        float x2 = (float) bb.maxX;
        float y2 = (float) bb.maxY;
        float z2 = (float) bb.maxZ;

        quad(buffer, matrix, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        quad(buffer, matrix, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, a);

        quad(buffer, matrix, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, a);
        quad(buffer, matrix, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        quad(buffer, matrix, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        quad(buffer, matrix, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
    }

    private void quad(VertexConsumer buffer, Matrix4f matrix,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      float r, float g, float b, float a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a);
        buffer.vertex(matrix, x4, y4, z4).color(r, g, b, a);
    }

    private void drawBoxOutline(VertexConsumer buffer, Matrix4f matrix, Box bb, int color, int alpha) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = alpha / 255.0f;

        double x1 = bb.minX, y1 = bb.minY, z1 = bb.minZ;
        double x2 = bb.maxX, y2 = bb.maxY, z2 = bb.maxZ;

        line(buffer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        line(buffer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        line(buffer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private void line(VertexConsumer buffer, Matrix4f matrix,
                      double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }

    private int mixWhite(int color, float k) {
        k = MathHelper.clamp(k, 0.0f, 1.0f);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = (int) MathHelper.lerp(k, r, 255);
        g = (int) MathHelper.lerp(k, g, 255);
        b = (int) MathHelper.lerp(k, b, 255);

        return (r << 16) | (g << 8) | b;
    }

    private float smoothStep(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private float easeOutExpo(float t) {
        return t >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
    }

    private static class GhostBox {
        final double minX, minY, minZ;
        final double maxX, maxY, maxZ;
        final long spawnTime;
        final long lifeTime;

        GhostBox(double minX, double minY, double minZ,
                 double maxX, double maxY, double maxZ,
                 long lifeTime) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.lifeTime = lifeTime;
            this.spawnTime = System.currentTimeMillis();
        }

        float getProgress() {
            long lived = System.currentTimeMillis() - spawnTime;
            return MathHelper.clamp((float) lived / (float) lifeTime, 0.0f, 1.0f);
        }

        Box getBox() {
            return new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "motion_ghost_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "motion_ghost_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static RenderLayer createLineLayer(String name, double width) {
        return RenderLayer.of(
                name,
                BUFFER_SIZE,
                false,
                true,
                LINE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(width)))
                        .build(false)
        );
    }

    private static final RenderLayer FILL_LAYER = RenderLayer.of(
            "motion_ghost_fill",
            BUFFER_SIZE,
            false,
            true,
            FILL_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    private static final RenderLayer LINE_LAYER_1 = createLineLayer("motion_ghost_line_1", 1.0);
    private static final RenderLayer LINE_LAYER_2 = createLineLayer("motion_ghost_line_2", 2.0);
    private static final RenderLayer LINE_LAYER_3 = createLineLayer("motion_ghost_line_3", 3.0);
    private static final RenderLayer LINE_LAYER_4 = createLineLayer("motion_ghost_line_4", 4.0);
    private static final RenderLayer LINE_LAYER_5 = createLineLayer("motion_ghost_line_5", 5.0);
    private static final RenderLayer LINE_LAYER_6 = createLineLayer("motion_ghost_line_6", 6.0);

    private static final RenderLayer GLOW_LAYER = createLineLayer("motion_ghost_glow", 4.0);
}