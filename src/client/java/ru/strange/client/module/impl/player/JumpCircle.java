package ru.strange.client.module.impl.player;

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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventJump;
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
import ru.strange.client.utils.animation.util.Animation;
import ru.strange.client.utils.animation.util.Easings;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@IModule(
        name = "JumpCircle",
        description = "Круги и world-scan эффекты прыжка",
        category = Category.Player,
        bind = -1
)
public class JumpCircle extends Module {

    private static final int BUFFER_SIZE = 1 << 10;
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;
    private static final String PIPELINE_NAMESPACE = "strange";
    private static final Identifier JUMP_TEXTURE = Identifier.of(Strange.rootRes, "textures/world/jump.png");
    private static final double SURFACE_CACHE_QUANTIZATION = 8.0;
    private static final int MAX_SURFACE_CACHE_ENTRIES = 2048;

    public static ModeSetting effectMode = new ModeSetting("Эффект", "Оба", "Круг", "Скан", "Оба");
    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(131, 166, 232));
    public static HueSetting accentColor = new HueSetting("Акцент", new Color(255, 255, 255));
    public static BooleanSetting shaderColors = new BooleanSetting("Shader Colors", false);
    public static ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !shaderColors.get());
    public static SliderSetting circleRadius = new SliderSetting("Circle Radius", 1.0f, 0.55f, 2.5f, 0.05f, false)
            .hidden(() -> effectMode.is("Скан"));
    public static SliderSetting scanRadius = new SliderSetting("Радиус скана", 14.0f, 6.0f, 28.0f, 1.0f, false)
            .hidden(() -> effectMode.is("Круг"));
    public static SliderSetting scanWidth = new SliderSetting("Ширина скана", 1.4f, 0.4f, 3.5f, 0.1f, false)
            .hidden(() -> effectMode.is("Круг"));
    public static SliderSetting scanLifetime = new SliderSetting("Длительность", 1.2f, 0.5f, 3.0f, 0.1f, false)
            .hidden(() -> effectMode.is("Круг"));
    public static SliderSetting scanSegments = new SliderSetting("Сегменты", 56, 24, 96, 4, false)
            .hidden(() -> effectMode.is("Круг"));
    public static SliderSetting scanWave = new SliderSetting("Волна", 0.18f, 0.0f, 0.6f, 0.02f, false)
            .hidden(() -> effectMode.is("Круг"));

    private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of(PIPELINE_NAMESPACE, "pipeline/world/textured_quads"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline SCAN_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of(PIPELINE_NAMESPACE, "pipeline/world/scan_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer SCAN_LINE_LAYER = RenderLayer.of(
            "strange_world_scan_line",
            BUFFER_SIZE,
            false,
            true,
            SCAN_LINE_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2.1)))
                    .build(false)
    );

    private static final RenderLayer CIRCLE_LAYER = RenderLayer.of(
            JUMP_TEXTURE.toString(),
            BUFFER_SIZE,
            false,
            true,
            TEXTURED_QUADS_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(new RenderPhase.Texture(JUMP_TEXTURE, false))
                    .build(false)
    );

    private final List<Circle> circles = new ArrayList<>();
    private final List<SurfaceScan> scans = new ArrayList<>();
    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);
    private final Vector3f reusableNormal = new Vector3f(0.0f, 0.0f, 1.0f);

    public JumpCircle() {
        addSettings(
                effectMode,
                colorSetting, accentColor,
                shaderColors, shaderTheme,
                circleRadius,
                scanRadius, scanWidth, scanLifetime, scanSegments, scanWave
        );
    }

    @EventInit
    public void onJump(EventJump event) {
        if (mc.player == null) {
            return;
        }
        spawnEffect(mc.player.getPos().add(0.0, 0.05, 0.0));
    }

    public void spawnExternal(Vec3d position) {
        if (!enable || position == null) {
            return;
        }
        spawnEffect(position);
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.world == null || mc.player == null) {
            clearEffects();
            return;
        }

        if (circles.isEmpty() && scans.isEmpty()) {
            return;
        }

        MatrixStack matrices = event.getMatrixStack();

        try {
            renderCircles(matrices, renderVertexConsumers);
            renderScans(matrices, renderVertexConsumers);
            renderVertexConsumers.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    @Override
    public void toggle() {
        super.toggle();
        clearEffects();
    }

    @Override
    public void onDisable() {
        clearEffects();
        super.onDisable();
    }

    @EventInit
    public void onChangeWorld(EventChangeWorld event) {
        clearEffects();
    }

    private void clearEffects() {
        circles.clear();
        scans.clear();
    }

    private void spawnEffect(Vec3d position) {
        if (!effectMode.is("Скан")) {
            circles.add(new Circle(position));
        }
        if (!effectMode.is("Круг")) {
            if (!Optimization.shouldLimitWorldEffects()) {
                scans.add(new SurfaceScan(position));
            }
        }
    }

    private int resolveBaseColor(double phase) {
        return shaderColors.get() ? ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), phase) : colorSetting.getRGB();
    }

    private int resolveAccentColor(double phase) {
        return shaderColors.get() ? ShaderThemeVisuals.animatedSecondary(shaderTheme.get(), phase) : accentColor.getRGB();
    }

    private void renderCircles(MatrixStack matrices, VertexConsumerProvider.Immediate immediate) {
        circles.removeIf(Circle::isDead);
        if (circles.isEmpty()) {
            return;
        }

        VertexConsumer buffer = immediate.getBuffer(CIRCLE_LAYER);
        for (Circle circle : circles) {
            circle.update();

            float progress = circle.getProgress();
            float pulse = 0.5f + 0.5f * (float) Math.sin(progress * Math.PI);
            int base = resolveBaseColor(circle.position.x * 0.16 + circle.position.z * 0.11 + progress * Math.PI * 2.0);
            int accent = resolveAccentColor(circle.position.x * 0.08 + circle.position.z * 0.19 + progress * Math.PI * 3.0);
            int color = RenderUtil.ColorUtil.interpolate(base, accent, pulse * 0.35f);
            float alpha = Math.max(0.0f, 1.0f - progress);
            renderCircleQuad(circle, matrices, buffer, color, alpha);
        }
    }

    private void renderCircleQuad(Circle circle, MatrixStack matrices, VertexConsumer buffer, int color, float alphaPc) {
        float alpha = Math.max(0.0f, Math.min(1.0f, alphaPc));
        if (alpha <= 0.01f) {
            return;
        }

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        float size = circleRadius.get() * (0.55f + 1.2f * (float) circle.animation.getValue());

        matrices.push();
        matrices.translate(circle.position.x - cameraPos.x, circle.position.y - cameraPos.y, circle.position.z - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (90.0 + 360.0 * circle.rotation.getValue())));

        MatrixStack.Entry entry = matrices.peek();
        drawTexturedQuad(
                buffer,
                entry.getPositionMatrix(),
                entry.getNormalMatrix(),
                -size / 2.0f,
                -size / 2.0f,
                size,
                size,
                RenderUtil.ColorUtil.replAlpha(color, (int) (255.0f * alpha))
        );
        matrices.pop();
    }

    private void renderScans(MatrixStack matrices, VertexConsumerProvider.Immediate immediate) {
        long now = System.currentTimeMillis();
        scans.removeIf(scan -> scan.isDead(now));
        if (scans.isEmpty()) {
            return;
        }

        VertexConsumer lineBuffer = immediate.getBuffer(SCAN_LINE_LAYER);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (SurfaceScan scan : scans) {
            float progress = scan.getProgress(now);
            float radiusValue = scanRadius.get() * easeOut(progress);
            float halfWidth = Math.max(0.16f, scanWidth.get() * (1.0f - progress * 0.45f));
            float alpha = 1.0f - progress;
            int segmentCount = Math.max(18, Optimization.capJumpCircleSegments((int) scanSegments.get()));

            int baseColor = resolveBaseColor(scan.origin.x * 0.09 + scan.origin.z * 0.12 + progress * Math.PI * 1.8);
            int accent = resolveAccentColor(scan.origin.x * 0.13 + scan.origin.z * 0.07 + progress * Math.PI * 2.6);
            int innerColor = RenderUtil.ColorUtil.replAlpha(baseColor, (int) (120.0f * alpha));
            int outerColor = RenderUtil.ColorUtil.replAlpha(
                    RenderUtil.ColorUtil.interpolate(baseColor, accent, 0.35f + 0.35f * progress),
                    (int) (180.0f * alpha)
            );
            int spokeColor = RenderUtil.ColorUtil.replAlpha(
                    RenderUtil.ColorUtil.interpolate(accent, baseColor, 0.45f),
                    (int) (210.0f * alpha)
            );
            int midColor = RenderUtil.ColorUtil.replAlpha(
                    RenderUtil.ColorUtil.interpolate(baseColor, accent, 0.55f),
                    (int) (145.0f * alpha)
            );

            for (int i = 0; i < segmentCount; i++) {
                double angle0 = (Math.PI * 2.0 * i) / segmentCount;
                double angle1 = (Math.PI * 2.0 * (i + 1)) / segmentCount;

                Vec3d inner0 = sampleSurfacePoint(scan, cameraPos, radiusValue - halfWidth, angle0, progress);
                Vec3d outer0 = sampleSurfacePoint(scan, cameraPos, radiusValue + halfWidth, angle0, progress);
                Vec3d inner1 = sampleSurfacePoint(scan, cameraPos, radiusValue - halfWidth, angle1, progress);
                Vec3d outer1 = sampleSurfacePoint(scan, cameraPos, radiusValue + halfWidth, angle1, progress);
                Vec3d mid0 = sampleSurfacePoint(scan, cameraPos, radiusValue, angle0, progress);
                Vec3d mid1 = sampleSurfacePoint(scan, cameraPos, radiusValue, angle1, progress);

                drawLine(lineBuffer, matrix, inner0, inner1, innerColor);
                drawLine(lineBuffer, matrix, outer0, outer1, outerColor);
                drawLine(lineBuffer, matrix, mid0, mid1, midColor);
                if ((i & 3) == 0) {
                    drawLine(lineBuffer, matrix, inner0, outer0, spokeColor);
                }
                if ((i & 7) == 2) {
                    drawLine(lineBuffer, matrix, mid0, outer0, RenderUtil.ColorUtil.replAlpha(accent, (int) (120.0f * alpha)));
                }
            }
        }
    }

    private Vec3d sampleSurfacePoint(SurfaceScan scan, Vec3d cameraPos, double radiusValue, double angle, float progress) {
        double actualRadius = Math.max(0.1, radiusValue);
        double worldX = scan.origin.x + Math.cos(angle) * actualRadius;
        double worldZ = scan.origin.z + Math.sin(angle) * actualRadius;
        double wave = Math.sin(angle * 6.0 + progress * 14.0) * scanWave.get() * (1.0 - progress) * 0.25;
        double worldY = resolveSurfaceY(scan, worldX, worldZ) + wave;
        return new Vec3d(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);
    }

    private double resolveSurfaceY(SurfaceScan scan, double worldX, double worldZ) {
        if (mc.world == null || mc.player == null) {
            return scan.origin.y;
        }

        long cacheKey = packSurfaceKey(worldX, worldZ);
        Double cachedY = scan.surfaceCache.get(cacheKey);
        if (cachedY != null) {
            return cachedY;
        }

        Vec3d start = new Vec3d(worldX, scan.origin.y + 2.75, worldZ);
        Vec3d end = new Vec3d(worldX, scan.origin.y - 5.5, worldZ);
        BlockHitResult hit = mc.world.raycast(
                new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)
        );

        double resolvedY;
        if (hit.getType() == HitResult.Type.BLOCK) {
            resolvedY = hit.getPos().y + 0.035;
        } else {
            BlockPos fallback = BlockPos.ofFloored(worldX, scan.origin.y - 0.1, worldZ);
            resolvedY = fallback.getY() + 0.035;
        }

        if (scan.surfaceCache.size() >= MAX_SURFACE_CACHE_ENTRIES) {
            scan.surfaceCache.clear();
        }
        scan.surfaceCache.put(cacheKey, resolvedY);
        return resolvedY;
    }

    private long packSurfaceKey(double worldX, double worldZ) {
        int quantizedX = (int) Math.floor(worldX * SURFACE_CACHE_QUANTIZATION);
        int quantizedZ = (int) Math.floor(worldZ * SURFACE_CACHE_QUANTIZATION);
        return (((long) quantizedX) << 32) ^ (quantizedZ & 0xFFFFFFFFL);
    }

    private float easeOut(float value) {
        float inverted = 1.0f - value;
        return 1.0f - inverted * inverted * inverted;
    }

    private void drawLine(VertexConsumer buffer, Matrix4f matrix, Vec3d start, Vec3d end, int color) {
        putVertex(buffer, matrix, start, color);
        putVertex(buffer, matrix, end, color);
    }

    private void putVertex(VertexConsumer buffer, Matrix4f matrix, Vec3d pos, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(r, g, b, a);
    }

    private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float width, float height, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        reusableNormal.set(0.0f, 0.0f, 1.0f);
        normalMatrix.transform(reusableNormal);
        reusableNormal.normalize();

        float x1 = x;
        float y1 = y;
        float x2 = x + width;
        float y2 = y + height;

        buffer.vertex(matrix, x1, y1, 0.0f).color(r, g, b, a).texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(reusableNormal.x, reusableNormal.y, reusableNormal.z);
        buffer.vertex(matrix, x2, y1, 0.0f).color(r, g, b, a).texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(reusableNormal.x, reusableNormal.y, reusableNormal.z);
        buffer.vertex(matrix, x2, y2, 0.0f).color(r, g, b, a).texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(reusableNormal.x, reusableNormal.y, reusableNormal.z);
        buffer.vertex(matrix, x1, y2, 0.0f).color(r, g, b, a).texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(reusableNormal.x, reusableNormal.y, reusableNormal.z);
    }

    private static final class Circle {
        private final Vec3d position;
        private final long startTime = System.currentTimeMillis();
        private final Animation animation = new Animation();
        private final Animation rotation = new Animation();
        private boolean reversing;

        private Circle(Vec3d position) {
            this.position = position;
            animation.run(2, 1, Easings.EXPO_OUT);
            rotation.run(1, 1, Easings.QUART_OUT);
        }

        private void update() {
            if (System.currentTimeMillis() - startTime > 1_900 && !reversing) {
                animation.run(0, 1, Easings.EXPO_IN);
                rotation.run(0, 1, Easings.QUART_IN);
                reversing = true;
            }
            animation.update();
            rotation.update();
        }

        private boolean isDead() {
            return System.currentTimeMillis() - startTime > 4_200;
        }

        private float getProgress() {
            return Math.min(1.0f, (System.currentTimeMillis() - startTime) / 4_200.0f);
        }
    }

    private final class SurfaceScan {
        private final Vec3d origin;
        private final long startTime = System.currentTimeMillis();
        private final long lifeTimeMs = Math.round(scanLifetime.get() * 1000.0f);
        private final Map<Long, Double> surfaceCache = new HashMap<>();

        private SurfaceScan(Vec3d origin) {
            this.origin = origin;
        }

        private boolean isDead(long now) {
            return now - startTime >= lifeTimeMs;
        }

        private float getProgress(long now) {
            return Math.min(1.0f, (now - startTime) / (float) lifeTimeMs);
        }
    }
}
