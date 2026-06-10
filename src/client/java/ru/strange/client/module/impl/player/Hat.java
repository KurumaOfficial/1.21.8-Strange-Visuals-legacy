package ru.strange.client.module.impl.player;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
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
import ru.strange.client.renderengine.renderers.util.MaskedCosmosRenderer;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.Color;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

@IModule(
        name = "Китайская шляпа",
        description = "Декоративная шляпа на игроке",
        category = Category.Player,
        bind = -1
)
public class Hat extends Module {

    private static final int BUFFER_SIZE = 1 << 16;
    private static final Map<Float, RenderLayer> HAT_LINE_LAYER_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, CircleLut> CIRCLE_CACHE = new ConcurrentHashMap<>();

    public ModeSetting colorMode = new ModeSetting("Цвет", "Один цвет", "Один цвет", "Два цвета", "Shader");
    public ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !colorMode.is("Shader"));
    public HueSetting color1 = new HueSetting("Цвет 1", new Color(131, 166, 232))
            .hidden(() -> colorMode.is("Shader"));
    public HueSetting color2 = new HueSetting("Цвет 2", new Color(232, 131, 166))
            .hidden(() -> !colorMode.is("Два цвета"));
    public SliderSetting alpha = new SliderSetting("Альфа", 255, 0, 255, 5, false);
    public SliderSetting segments = new SliderSetting("Сегменты", 120, 60, 240, 10, false);
    public SliderSetting height = new SliderSetting("Высота", 0.25f, 0.1f, 0.5f, 0.01f, false);
    public SliderSetting radius = new SliderSetting("Ширина", 0.55f, 0.3f, 1.0f, 0.05f, false);
    public BooleanSetting outline = new BooleanSetting("Контур", true);
    public SliderSetting outlineWidth = new SliderSetting("Толщина контура", 2.5f, 1.0f, 5.0f, 0.5f, false)
            .hidden(() -> !outline.get());
    public BooleanSetting rotate = new BooleanSetting("Вращение", true)
            .hidden(() -> !colorMode.is("Два цвета"));
    public SliderSetting rotationSpeed = new SliderSetting("Скорость вращения", 1.0f, 0.1f, 5.0f, 0.1f, false)
            .hidden(() -> !rotate.get() || !colorMode.is("Два цвета"));

    private static final RenderPipeline HAT_FILL_OPAQUE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "hat_fill_opaque"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .build()
    );

    private static final RenderPipeline HAT_FILL_TRANSPARENT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "hat_fill_transparent"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderPipeline HAT_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "hat_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderPipeline HAT_MASK_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "hat_shader_mask"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderLayer HAT_MASK_LAYER = RenderLayer.of(
            "strange_hat_shader_mask",
            BUFFER_SIZE,
            false,
            true,
            HAT_MASK_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    private static final RenderLayer HAT_FILL_OPAQUE_LAYER = createHatFillLayer("strange_hat_fill_opaque", HAT_FILL_OPAQUE_PIPELINE);
    private static final RenderLayer HAT_FILL_TRANSPARENT_LAYER = createHatFillLayer("strange_hat_fill_transparent", HAT_FILL_TRANSPARENT_PIPELINE);

    private float rotationAngle;
    private final BufferAllocator renderBufferAllocator = new BufferAllocator(BUFFER_SIZE);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);

    public Hat() {
        addSettings(colorMode, shaderTheme, color1, color2, alpha, segments, height, radius, outline, outlineWidth, rotate, rotationSpeed);
    }

    @Override
    public void onDisable() {
        rotationAngle = 0.0f;
        super.onDisable();
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.world == null || mc.player == null || mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            return;
        }

        MatrixStack matrices = event.getMatrixStack();
        float partialTicks = event.getTickDelta();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        ItemStack helmet = mc.player.getEquippedStack(EquipmentSlot.HEAD);

        double x = mc.player.lastRenderX + (mc.player.getX() - mc.player.lastRenderX) * partialTicks;
        double y = mc.player.lastRenderY + (mc.player.getY() - mc.player.lastRenderY) * partialTicks;
        double z = mc.player.lastRenderZ + (mc.player.getZ() - mc.player.lastRenderZ) * partialTicks;
        double hatY = y + mc.player.getHeight() - (!helmet.isEmpty() ? -0.08f : mc.player.isSneaking() ? 0.1f : 0.03f);

        matrices.push();
        matrices.translate(x - cameraPos.x, hatY - cameraPos.y, z - cameraPos.z);

        if (colorMode.is("Два цвета") && rotate.get()) {
            rotationAngle += rotationSpeed.get() * 0.5f * partialTicks;
            if (rotationAngle >= 360.0f) {
                rotationAngle -= 360.0f;
            }
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationAngle));
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        if (colorMode.is("Shader")) {
            renderShaderHat(matrix);
        } else {
            renderRegularHat(matrix);
        }

        matrices.pop();
    }

    private void renderRegularHat(Matrix4f matrix) {
        VertexConsumerProvider.Immediate immediate = renderVertexConsumers;

        try {
            renderFill(immediate, matrix);
            renderOutline(immediate, matrix, false);
            immediate.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    private void renderShaderHat(Matrix4f matrix) {
        int alphaValue = (int) alpha.get();
        if (alphaValue <= 0 && !outline.get()) {
            return;
        }

        ShaderThemePreset preset = ShaderThemePreset.byName(shaderTheme.get());
        boolean pulseTheme = preset.isPulse();
        MaskedCosmosRenderer.getInstance().render(
                immediate -> renderMask(immediate.getBuffer(HAT_MASK_LAYER), matrix, alphaValue),
                42.0f * preset.densityScale(),
                0.28f * preset.speedScale(),
                0.96f * preset.intensityScale(),
                preset.primaryColor(),
                preset.accentColor(),
                0.36f * preset.edgeScale(),
                preset.themeIndex(),
                preset.patternScale(),
                preset.sparkleScale(),
                preset.starMix(),
                pulseTheme ? 0.86f : 1.0f,
                pulseTheme
        );

        VertexConsumerProvider.Immediate immediate = renderVertexConsumers;

        try {
            renderOutline(immediate, matrix, true);
            immediate.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    private void renderFill(VertexConsumerProvider.Immediate immediate, Matrix4f matrix) {
        int segmentCount = (int) segments.get();
        int alphaValue = (int) alpha.get();
        if (alphaValue <= 0) {
            return;
        }

        VertexConsumer fillBuffer = immediate.getBuffer(getHatFillLayer(alphaValue));
        float hatRadius = radius.get();
        float hatHeight = height.get();
        int centerColor = getCenterColor(alphaValue);
        CircleLut circle = getCircleLut(segmentCount);

        for (int i = 0; i < segmentCount; i++) {
            fillBuffer.vertex(matrix, 0.0f, hatHeight, 0.0f).color(centerColor);
            fillBuffer.vertex(matrix, circle.xAt(i) * hatRadius, 0.0f, circle.zAt(i) * hatRadius).color(getSegmentColor(i, segmentCount, alphaValue));
            fillBuffer.vertex(matrix, circle.xAt(i + 1) * hatRadius, 0.0f, circle.zAt(i + 1) * hatRadius).color(getSegmentColor(i + 1, segmentCount, alphaValue));
        }
    }

    private void renderMask(VertexConsumer buffer, Matrix4f matrix, int alphaValue) {
        if (alphaValue <= 0) {
            return;
        }

        int segmentCount = (int) segments.get();
        float hatRadius = radius.get();
        float hatHeight = height.get();
        int color = RenderUtil.ColorUtil.replAlpha(0xFFFFFFFF, alphaValue);
        CircleLut circle = getCircleLut(segmentCount);

        for (int i = 0; i < segmentCount; i++) {
            buffer.vertex(matrix, 0.0f, hatHeight, 0.0f).color(color);
            buffer.vertex(matrix, circle.xAt(i) * hatRadius, 0.0f, circle.zAt(i) * hatRadius).color(color);
            buffer.vertex(matrix, circle.xAt(i + 1) * hatRadius, 0.0f, circle.zAt(i + 1) * hatRadius).color(color);
        }
    }

    private void renderOutline(VertexConsumerProvider.Immediate immediate, Matrix4f matrix, boolean shaderOutline) {
        if (!outline.get()) {
            return;
        }

        int segmentCount = (int) segments.get();
        int alphaValue = Math.max((int) alpha.get(), 40);
        float hatRadius = radius.get();
        VertexConsumer lineBuffer = immediate.getBuffer(getHatLineLayer());
        CircleLut circle = getCircleLut(segmentCount);

        for (int i = 0; i < segmentCount; i++) {
            int lineColor1 = shaderOutline ? getShaderOutlineColor(i, segmentCount, alphaValue) : getSegmentColor(i, segmentCount, alphaValue);
            int lineColor2 = shaderOutline ? getShaderOutlineColor(i + 1, segmentCount, alphaValue) : getSegmentColor(i + 1, segmentCount, alphaValue);

            lineBuffer.vertex(matrix, circle.xAt(i) * hatRadius, 0.0f, circle.zAt(i) * hatRadius).color(lineColor1);
            lineBuffer.vertex(matrix, circle.xAt(i + 1) * hatRadius, 0.0f, circle.zAt(i + 1) * hatRadius).color(lineColor2);
        }
    }

    private static CircleLut getCircleLut(int segmentCount) {
        return CIRCLE_CACHE.computeIfAbsent(Math.max(3, segmentCount), CircleLut::new);
    }

    private static final class CircleLut {
        private final float[] xs;
        private final float[] zs;

        private CircleLut(int segmentCount) {
            this.xs = new float[segmentCount + 1];
            this.zs = new float[segmentCount + 1];

            for (int i = 0; i <= segmentCount; i++) {
                float angle = (float) Math.toRadians(i * (360.0 / segmentCount));
                xs[i] = (float) Math.cos(angle);
                zs[i] = (float) Math.sin(angle);
            }
        }

        private float xAt(int index) {
            return xs[index];
        }

        private float zAt(int index) {
            return zs[index];
        }
    }

    private int getSegmentColor(int index, int segmentCount, int alphaValue) {
        if (colorMode.is("Один цвет")) {
            return RenderUtil.ColorUtil.replAlpha(color1.getRGB(), alphaValue);
        }

        float percent = (index / (float) segmentCount) * 100.0f;
        int block = (int) (percent / 10.0f) % 2;
        int rgb = block == 0 ? color1.getRGB() : color2.getRGB();
        return RenderUtil.ColorUtil.replAlpha(rgb, alphaValue);
    }

    private int getCenterColor(int alphaValue) {
        if (colorMode.is("Один цвет")) {
            return RenderUtil.ColorUtil.replAlpha(color1.getRGB(), alphaValue);
        }

        int mixed = RenderUtil.ColorUtil.interpolateColor(color1.getRGB(), color2.getRGB(), 0.5f);
        return RenderUtil.ColorUtil.replAlpha(mixed, alphaValue);
    }

    private int getShaderOutlineColor(int index, int segmentCount, int alphaValue) {
        double phase = (index / (double) Math.max(1, segmentCount)) * Math.PI * 2.0;
        int animated = ShaderThemeVisuals.animatedSecondary(shaderTheme.get(), phase);
        return ShaderThemeVisuals.applyAlpha(animated, alphaValue / 255.0f);
    }

    private RenderLayer getHatFillLayer(int alphaValue) {
        return alphaValue >= 255 ? HAT_FILL_OPAQUE_LAYER : HAT_FILL_TRANSPARENT_LAYER;
    }

    private RenderLayer getHatLineLayer() {
        return HAT_LINE_LAYER_CACHE.computeIfAbsent(outlineWidth.get(), Hat::createHatLineLayer);
    }

    private static RenderLayer createHatFillLayer(String name, RenderPipeline pipeline) {
        return RenderLayer.of(
                name,
                BUFFER_SIZE,
                false,
                true,
                pipeline,
                RenderLayer.MultiPhaseParameters.builder().build(false)
        );
    }

    private static RenderLayer createHatLineLayer(float width) {
        return RenderLayer.of(
                "strange_hat_line_" + Float.floatToIntBits(width),
                BUFFER_SIZE,
                false,
                true,
                HAT_LINE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(width)))
                        .build(false)
        );
    }
}
