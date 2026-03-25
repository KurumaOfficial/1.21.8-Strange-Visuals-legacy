package ru.strange.client.module.impl.world;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
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
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.renderengine.renderers.util.BlockCosmosRenderer;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;

import java.awt.*;
import java.util.OptionalDouble;

@IModule(
        name = "Обводка блока",
        description = "Красивые режимы обводки блока под прицелом",
        category = Category.World,
        bind = -1
)
public class BlockOutline extends Module {

    private static final int BUFFER_SIZE = 1 << 12;
    private static final Identifier GLOW_TEXTURE = Identifier.of("strange", "textures/world/glow.png");

    public final ModeSetting mode = new ModeSetting("Режим", "Хитбокс", "Хитбокс", "Шейдер");
    public final SliderSetting animSpeed = new SliderSetting("Скорость", 1.0f, 0.2f, 3.0f, 0.1f, false)
            .hidden(() -> mode.is("Хитбокс"));
    public final SliderSetting fillAlpha = new SliderSetting("Прозрачность", 92.0f, 20.0f, 200.0f, 2.0f, false);
    public final SliderSetting lineAlpha = new SliderSetting("Яркость линии", 255.0f, 100.0f, 255.0f, 5.0f, false);

    public final HueSetting hitboxColor = new HueSetting("Цвет хитбокса", 0.0f, 0.0f, 1.0f)
            .hidden(() -> !mode.is("Хитбокс"));

    public final HueSetting cosmosColor = new HueSetting("Цвет шейдера", 77.0f, 0.86f, 0.78f)
            .hidden(() -> !mode.is("Шейдер"));
    public final SliderSetting starAmount = new SliderSetting("Плотность", 120.0f, 30.0f, 300.0f, 5.0f, false)
            .hidden(() -> !mode.is("Шейдер"));
    public final ModeSetting shaderTheme = new ModeSetting("Тема шейдера", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !mode.is("Шейдер"));

    public final HueSetting webColor = new HueSetting("Цвет паутины", 79.0f, 0.84f, 0.72f)
            .hidden(() -> !mode.is("Паутина"));
    public final SliderSetting webDensity = new SliderSetting("Плотность", 16.0f, 6.0f, 30.0f, 1.0f, false)
            .hidden(() -> !mode.is("Паутина"));

    public final HueSetting plasmaColor = new HueSetting("Цвет плазмы", 58.0f, 0.55f, 0.82f)
            .hidden(() -> !mode.is("Плазма"));
    public final SliderSetting plasmaAmount = new SliderSetting("Сгустки", 11.0f, 4.0f, 18.0f, 1.0f, false)
            .hidden(() -> !mode.is("Плазма"));

    public final SliderSetting pulseAlpha = new SliderSetting("Pulse Alpha", 0.75f, 0.05f, 1.5f, 0.05f, false)
            .hidden(() -> !mode.is("Шейдер") || !ShaderThemePreset.byName(shaderTheme.get()).isPulse());

    private final long startTime = System.nanoTime();

    public BlockOutline() {
        pulseAlpha.hidden = () -> !mode.is("Шейдер") || !ShaderThemePreset.byName(shaderTheme.get()).isPulse();
        addSettings(mode, animSpeed, fillAlpha, lineAlpha,
                hitboxColor,
                cosmosColor, starAmount, shaderTheme, pulseAlpha);
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState state = mc.world.getBlockState(blockPos);
        if (state.isAir()) {
            return;
        }

        VoxelShape shape = state.getOutlineShape(mc.world, blockPos, ShapeContext.of(mc.player));
        if (shape.isEmpty()) {
            return;
        }

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = event.getMatrixStack();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0) * animSpeed.get();

        BufferAllocator allocator = new BufferAllocator(1 << 18);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        try {
            shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                double x1 = blockPos.getX() + minX - cameraPos.x - 0.0025;
                double y1 = blockPos.getY() + minY - cameraPos.y - 0.0025;
                double z1 = blockPos.getZ() + minZ - cameraPos.z - 0.0025;
                double x2 = blockPos.getX() + maxX - cameraPos.x + 0.0025;
                double y2 = blockPos.getY() + maxY - cameraPos.y + 0.0025;
                double z2 = blockPos.getZ() + maxZ - cameraPos.z + 0.0025;

                switch (mode.get()) {
                    case "Хитбокс" -> renderHitboxBox(immediate, matrix, x1, y1, z1, x2, y2, z2, time);
                    case "Шейдер" -> renderCosmosBox(immediate, matrix, x1, y1, z1, x2, y2, z2, time);
                }
            });

            immediate.draw();
        } finally {
            allocator.close();
        }
    }

    private void renderHitboxBox(VertexConsumerProvider.Immediate immediate, Matrix4f matrix,
                                 double x1, double y1, double z1, double x2, double y2, double z2,
                                 float time) {
        Color base = hitboxColor.getColor();
        int fillA = (int) fillAlpha.get();
        int lineA = (int) lineAlpha.get();

        VertexConsumer fillBuffer = immediate.getBuffer(FILL_LAYER);
        int fill = rgba(
                clamp01(base.getRed() / 255.0f * 0.40f),
                clamp01(base.getGreen() / 255.0f * 0.40f),
                clamp01(base.getBlue() / 255.0f * 0.40f),
                (int) (fillA * 0.45f)
        );
        drawFillFaces(fillBuffer, matrix, x1, y1, z1, x2, y2, z2, fill8(fill));

        VertexConsumer lineBuffer = immediate.getBuffer(OUTLINE_NO_DEPTH_LAYER);
        float pulse = 0.80f + 0.20f * (float) Math.sin(time * 2.0f);
        int edge = rgba(
                clamp01(base.getRed() / 255.0f * pulse),
                clamp01(base.getGreen() / 255.0f * pulse),
                clamp01(base.getBlue() / 255.0f * pulse),
                lineA
        );
        drawBoxEdges(lineBuffer, matrix, x1, y1, z1, x2, y2, z2, fill8(edge));
    }

    private void renderCosmosBox(VertexConsumerProvider.Immediate immediate, Matrix4f matrix,
                                 double x1, double y1, double z1, double x2, double y2, double z2,
                                 float time) {
        ShaderThemePreset preset = ShaderThemePreset.byName(shaderTheme.get());
        int userTint = cosmosColor.getColor().getRGB() | 0xFF000000;
        boolean pulseTheme = preset.isPulse();
        int primaryArgb = pulseTheme ? userTint : ShaderThemePreset.mixColors(preset.primaryColor(), userTint, 0.28f);
        int accentArgb = pulseTheme
                ? ShaderThemePreset.mixColors(preset.accentColor(), userTint, 0.38f)
                : ShaderThemePreset.mixColors(preset.accentColor(), userTint, 0.16f);
        Color accent = new Color(accentArgb, true);
        int lineA = (int) lineAlpha.get();

        float density = (starAmount.get() / 4.0f) * preset.densityScale();
        float speed = animSpeed.get() * 0.3f * preset.speedScale();
        BlockCosmosRenderer.getInstance().render(
                matrix, x1, y1, z1, x2, y2, z2,
                density, speed, 0.8f * preset.intensityScale(), primaryArgb, accentArgb,
                0.3f * preset.edgeScale(), preset.themeIndex(), preset.patternScale(), preset.sparkleScale(), preset.starMix(),
                pulseTheme ? pulseAlpha.get() : 1.0f, false
        );

        float edgePulse = 0.75f + 0.25f * (float) Math.sin(time * 1.8f);
        VertexConsumer lineBuffer = immediate.getBuffer(OUTLINE_NO_DEPTH_LAYER);
        int edgeColor = rgba(
                clamp01(accent.getRed() / 255.0f * 1.08f + 0.10f * edgePulse),
                clamp01(accent.getGreen() / 255.0f * 0.92f + 0.04f * edgePulse),
                clamp01(accent.getBlue() / 255.0f * 1.12f + 0.16f * edgePulse),
                (int) (lineA * edgePulse)
        );
        drawBoxEdges(lineBuffer, matrix, x1, y1, z1, x2, y2, z2, fill8(edgeColor));
    }

    private void renderWebBox(VertexConsumerProvider.Immediate immediate, Matrix4f matrix,
                              double x1, double y1, double z1, double x2, double y2, double z2,
                              float time, long seed) {
        ShaderThemePreset preset = ShaderThemePreset.COBWEB;
        int userTint = webColor.getColor().getRGB() | 0xFF000000;
        int primaryArgb = ShaderThemePreset.mixColors(preset.primaryColor(), userTint, 0.42f);
        int accentArgb = ShaderThemePreset.mixColors(preset.accentColor(), userTint, 0.22f);
        Color accent = new Color(accentArgb, true);
        int lineA = (int) lineAlpha.get();

        float density = (webDensity.get() * 3.8f) * preset.densityScale();
        float speed = animSpeed.get() * 0.16f * preset.speedScale();
        float patternScale = preset.patternScale() * (0.84f + webDensity.get() * 0.045f);

        BlockCosmosRenderer.getInstance().render(
                matrix, x1, y1, z1, x2, y2, z2,
                density, speed, 0.94f * preset.intensityScale(), primaryArgb, accentArgb,
                0.18f * preset.edgeScale(), preset.themeIndex(), patternScale, preset.sparkleScale(), preset.starMix(),
                1.0f, false
        );

        float edgePulse = 0.58f + 0.42f * (float) Math.sin(time * 1.45f + seed * 0.0018f);
        VertexConsumer lineBuffer = immediate.getBuffer(OUTLINE_NO_DEPTH_LAYER);
        int edgeColor = rgba(
                clamp01(accent.getRed() / 255.0f * 0.82f + 0.14f * edgePulse),
                clamp01(accent.getGreen() / 255.0f * 0.78f + 0.10f * edgePulse),
                clamp01(accent.getBlue() / 255.0f * 1.08f + 0.18f * edgePulse),
                (int) (lineA * (0.52f + edgePulse * 0.24f))
        );
        drawBoxEdges(lineBuffer, matrix, x1, y1, z1, x2, y2, z2, fill8(edgeColor));
    }

    private void renderPlasmaBox(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Matrix4f matrix,
                                 double x1, double y1, double z1, double x2, double y2, double z2,
                                 float time, long seed) {
        Color base = plasmaColor.getColor();
        int fillA = (int) fillAlpha.get();
        int lineA = (int) lineAlpha.get();

        VertexConsumer fillBuffer = immediate.getBuffer(FILL_LAYER);
        int dark = rgba(0.02f, 0.03f, 0.05f, (int) (fillA * 0.9f));
        int mid = rgba(
                clamp01(base.getRed() / 255.0f * 0.50f),
                clamp01(base.getGreen() / 255.0f * 0.68f),
                clamp01(base.getBlue() / 255.0f * 0.92f + 0.08f),
                fillA
        );
        drawFillFaces(fillBuffer, matrix, x1, y1, z1, x2, y2, z2, new int[]{dark, mid, dark, mid, mid, dark, mid, dark});

        VertexConsumer lineBuffer = immediate.getBuffer(OUTLINE_NO_DEPTH_LAYER);
        int edge = rgba(
                clamp01(base.getRed() / 255.0f * 0.9f + 0.08f),
                clamp01(base.getGreen() / 255.0f * 1.05f + 0.18f),
                clamp01(base.getBlue() / 255.0f * 1.12f + 0.22f),
                lineA
        );
        drawBoxEdges(lineBuffer, matrix, x1, y1, z1, x2, y2, z2, fill8(edge));

        VertexConsumer glowBuffer = immediate.getBuffer(GLOW_LAYER);
        int blobs = (int) plasmaAmount.get();
        for (int i = 0; i < blobs; i++) {
            float h1 = hash(seed * 0.081f + i * 2.17f);
            float h2 = hash(seed * 0.111f + i * 7.37f);
            float h3 = hash(seed * 0.147f + i * 4.57f);
            float orbit = time * (0.45f + h1 * 0.6f) + i * 0.7f;

            float px = (float) (x1 + (x2 - x1) * (0.5f + 0.34f * Math.sin(orbit + h2 * 4.0f)));
            float py = (float) (y1 + (y2 - y1) * (0.5f + 0.34f * Math.cos(orbit * 0.8f + h3 * 5.0f)));
            float pz = (float) (z1 + (z2 - z1) * (0.5f + 0.34f * Math.sin(orbit * 1.2f + h1 * 3.0f)));

            int blobColor = rgba(
                    clamp01(base.getRed() / 255.0f * (0.65f + h2 * 0.25f)),
                    clamp01(base.getGreen() / 255.0f * (0.90f + h3 * 0.25f) + 0.08f),
                    clamp01(base.getBlue() / 255.0f * (1.05f + h1 * 0.25f) + 0.15f),
                    (int) (72 + 75 * (0.5f + 0.5f * Math.sin(orbit)))
            );

            matrices.push();
            matrices.translate(px, py, pz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
            drawGlowQuad(glowBuffer, matrices.peek().getPositionMatrix(), blobColor, 0.10f + 0.07f * h3, 1.0f);
            matrices.pop();
        }
    }

    private static void drawFillFaces(VertexConsumer buffer, Matrix4f matrix,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2,
                                      int[] c) {
        vtx(buffer, matrix, x1, y1, z1, c[0]);
        vtx(buffer, matrix, x2, y1, z1, c[1]);
        vtx(buffer, matrix, x2, y1, z2, c[2]);
        vtx(buffer, matrix, x1, y1, z2, c[3]);

        vtx(buffer, matrix, x1, y2, z1, c[4]);
        vtx(buffer, matrix, x1, y2, z2, c[7]);
        vtx(buffer, matrix, x2, y2, z2, c[6]);
        vtx(buffer, matrix, x2, y2, z1, c[5]);

        vtx(buffer, matrix, x1, y1, z2, c[3]);
        vtx(buffer, matrix, x2, y1, z2, c[2]);
        vtx(buffer, matrix, x2, y2, z2, c[6]);
        vtx(buffer, matrix, x1, y2, z2, c[7]);

        vtx(buffer, matrix, x2, y1, z1, c[1]);
        vtx(buffer, matrix, x1, y1, z1, c[0]);
        vtx(buffer, matrix, x1, y2, z1, c[4]);
        vtx(buffer, matrix, x2, y2, z1, c[5]);

        vtx(buffer, matrix, x1, y1, z1, c[0]);
        vtx(buffer, matrix, x1, y1, z2, c[3]);
        vtx(buffer, matrix, x1, y2, z2, c[7]);
        vtx(buffer, matrix, x1, y2, z1, c[4]);

        vtx(buffer, matrix, x2, y1, z2, c[2]);
        vtx(buffer, matrix, x2, y1, z1, c[1]);
        vtx(buffer, matrix, x2, y2, z1, c[5]);
        vtx(buffer, matrix, x2, y2, z2, c[6]);
    }

    private static void vtx(VertexConsumer buffer, Matrix4f matrix, double x, double y, double z, int color) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(red(color), green(color), blue(color), alpha(color));
    }

    private static void drawBoxEdges(VertexConsumer buffer, Matrix4f matrix,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ,
                                     int[] colors) {
        drawLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, colors[0], colors[1]);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, colors[1], colors[2]);
        drawLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, colors[2], colors[3]);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, colors[3], colors[0]);

        drawLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, colors[4], colors[5]);
        drawLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, colors[5], colors[6]);
        drawLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, colors[6], colors[7]);
        drawLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, colors[7], colors[4]);

        drawLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, colors[0], colors[4]);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, colors[1], colors[5]);
        drawLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, colors[2], colors[6]);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, colors[3], colors[7]);
    }

    private static void drawLine(VertexConsumer buffer, Matrix4f matrix,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 int color1, int color2) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1)
                .color(red(color1), green(color1), blue(color1), alpha(color1));
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2)
                .color(red(color2), green(color2), blue(color2), alpha(color2));
    }

    private static void drawGlowQuad(VertexConsumer buffer, Matrix4f matrix, int color, float size, float stretchY) {
        float sx = size;
        float sy = size * stretchY;
        buffer.vertex(matrix, -sx, -sy, 0.0f).texture(0.0f, 1.0f).color(red(color), green(color), blue(color), alpha(color))
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, sx, -sy, 0.0f).texture(1.0f, 1.0f).color(red(color), green(color), blue(color), alpha(color))
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, sx, sy, 0.0f).texture(1.0f, 0.0f).color(red(color), green(color), blue(color), alpha(color))
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, -sx, sy, 0.0f).texture(0.0f, 0.0f).color(red(color), green(color), blue(color), alpha(color))
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    private static int[] fill8(int color) {
        return new int[]{color, color, color, color, color, color, color, color};
    }

    private static int rgba(float r, float g, float b, int a) {
        return rgba(clamp255(r), clamp255(g), clamp255(b), a);
    }

    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int red(int color) {
        return color >> 16 & 255;
    }

    private static int green(int color) {
        return color >> 8 & 255;
    }

    private static int blue(int color) {
        return color & 255;
    }

    private static int alpha(int color) {
        return color >> 24 & 255;
    }

    private static int clamp255(float value) {
        return Math.max(0, Math.min(255, (int) (value * 255.0f)));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float hash(float value) {
        double sin = Math.sin(value) * 43758.5453123;
        return (float) (sin - Math.floor(sin));
    }

    private static final BlendFunction ALPHA_BLEND = new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ZERO, DestFactor.ONE
    );

    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "block_outline_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline OUTLINE_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "block_outline_lines_no_depth"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "block_outline_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer FILL_LAYER = RenderLayer.of(
            "strange_block_outline_fill",
            BUFFER_SIZE,
            false,
            true,
            FILL_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    private static final RenderLayer OUTLINE_NO_DEPTH_LAYER = RenderLayer.of(
            "strange_block_outline_no_depth",
            BUFFER_SIZE,
            false,
            true,
            OUTLINE_NO_DEPTH_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2.0)))
                    .build(false)
    );

    private static final RenderLayer GLOW_LAYER = RenderLayer.of(
            "strange_block_outline_glow",
            BUFFER_SIZE,
            false,
            true,
            GLOW_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(new RenderPhase.Texture(GLOW_TEXTURE, false))
                    .build(false)
    );
}
