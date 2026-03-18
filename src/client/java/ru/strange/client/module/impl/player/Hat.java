package ru.strange.client.module.impl.player;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
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
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.OptionalDouble;

@IModule(
        name = "Китайская шляпа",
        description = " ",
        category = Category.Player,
        bind = -1
)
public class Hat extends Module {

    private static final int BUFFER_SIZE = 1 << 16;

    public ModeSetting colorMode = new ModeSetting("Цвет", "Один цвет", "Один цвет", "Два цвета");
    public HueSetting color1 = new HueSetting("Цвет 1", new Color(131, 166, 232));
    public HueSetting color2 = new HueSetting("Цвет 2", new Color(232, 131, 166)).hidden(() -> !colorMode.is("Два цвета"));

    // вместо "Прозрачность" теперь понятная альфа
    public SliderSetting alpha = new SliderSetting("Альфа", 255, 0, 255, 5, false);

    public SliderSetting segments = new SliderSetting("Сегменты", 120, 60, 240, 10, false);
    public SliderSetting height = new SliderSetting("Высота", 0.25f, 0.1f, 0.5f, 0.01f, false);
    public SliderSetting radius = new SliderSetting("Ширина", 0.55f, 0.3f, 1.0f, 0.05f, false);

    public BooleanSetting outline = new BooleanSetting("Контур", true);
    public SliderSetting outlineWidth = new SliderSetting("Толщина контура", 2.5f, 1.0f, 5.0f, 0.5f, false).hidden(() -> !outline.get());

    public BooleanSetting rotate = new BooleanSetting("Вращение", true).hidden(() -> !colorMode.is("Два цвета"));
    public SliderSetting rotationSpeed = new SliderSetting("Скорость вращения", 1.0f, 0.1f, 5.0f, 0.1f, false)
            .hidden(() -> !rotate.get() || !colorMode.is("Два цвета"));

    private float rotationAngle = 0f;

    public Hat() {
        addSettings(colorMode, color1, color2, alpha, segments, height, radius, outline, outlineWidth, rotate, rotationSpeed);
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.world == null || mc.player == null) return;
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) return;

        BufferAllocator allocator = new BufferAllocator(BUFFER_SIZE);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        MatrixStack matrices = event.getMatrixStack();
        float partialTicks = event.getTickDelta();

        try {
            Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
            ItemStack helmet = mc.player.getEquippedStack(EquipmentSlot.HEAD);

            double x = mc.player.lastRenderX + (mc.player.getX() - mc.player.lastRenderX) * partialTicks;
            double y = mc.player.lastRenderY + (mc.player.getY() - mc.player.lastRenderY) * partialTicks;
            double z = mc.player.lastRenderZ + (mc.player.getZ() - mc.player.lastRenderZ) * partialTicks;

            double hatY = y + mc.player.getHeight() - (!helmet.isEmpty() ? -0.08f : mc.player.isSneaking() ? 0.1f : 0.03f);

            matrices.push();
            matrices.translate(x - cameraPos.x, hatY - cameraPos.y, z - cameraPos.z);

            if (colorMode.is("Два цвета") && rotate.get()) {
                rotationAngle += rotationSpeed.get() * 0.5f;
                if (rotationAngle >= 360f) rotationAngle -= 360f;
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationAngle));
            }

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            renderChinaHat(immediate, matrix);

            matrices.pop();
            immediate.draw();
        } finally {
            allocator.close();
        }
    }

    private void renderChinaHat(VertexConsumerProvider.Immediate immediate, Matrix4f matrix) {
        int segmentCount = (int) segments.get();
        float hatRadius = radius.get();
        float hatHeight = height.get();
        int alphaValue = (int) alpha.get();

        if (alphaValue <= 0 && !outline.get()) return;

        VertexConsumer fillBuffer = immediate.getBuffer(getHatFillLayer(alphaValue));

        for (int i = 0; i < segmentCount; i++) {
            float angle1 = (float) Math.toRadians(i * (360.0 / segmentCount));
            float angle2 = (float) Math.toRadians((i + 1) * (360.0 / segmentCount));

            int edgeColor1 = getSegmentColor(i, segmentCount, alphaValue);
            int edgeColor2 = getSegmentColor(i + 1, segmentCount, alphaValue);
            int centerColor = getCenterColor(alphaValue);

            fillBuffer.vertex(matrix, 0, hatHeight, 0).color(centerColor);
            fillBuffer.vertex(matrix, (float) Math.cos(angle1) * hatRadius, 0, (float) Math.sin(angle1) * hatRadius).color(edgeColor1);
            fillBuffer.vertex(matrix, (float) Math.cos(angle2) * hatRadius, 0, (float) Math.sin(angle2) * hatRadius).color(edgeColor2);
        }

        if (outline.get()) {
            VertexConsumer lineBuffer = immediate.getBuffer(getHatLineLayer(alphaValue));

            for (int i = 0; i < segmentCount; i++) {
                float angle1 = (float) Math.toRadians(i * (360.0 / segmentCount));
                float angle2 = (float) Math.toRadians((i + 1) * (360.0 / segmentCount));

                int lineColor1 = getSegmentColor(i, segmentCount, Math.max(alphaValue, 40));
                int lineColor2 = getSegmentColor(i + 1, segmentCount, Math.max(alphaValue, 40));

                lineBuffer.vertex(matrix, (float) Math.cos(angle1) * hatRadius, 0, (float) Math.sin(angle1) * hatRadius).color(lineColor1);
                lineBuffer.vertex(matrix, (float) Math.cos(angle2) * hatRadius, 0, (float) Math.sin(angle2) * hatRadius).color(lineColor2);
            }
        }
    }

    private int getSegmentColor(int index, int segmentCount, int alphaValue) {
        if (colorMode.is("Один цвет")) {
            return RenderUtil.ColorUtil.replAlpha(color1.getRGB(), alphaValue);
        }

        float percent = (index / (float) segmentCount) * 100f;
        int block = (int) (percent / 10f) % 2;
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

    private RenderLayer getHatFillLayer(int alphaValue) {
        boolean opaque = alphaValue >= 255;

        return RenderLayer.of(
                opaque ? "strange_hat_fill_opaque" : "strange_hat_fill_transparent",
                BUFFER_SIZE,
                false,
                true,
                opaque ? HAT_FILL_OPAQUE_PIPELINE : HAT_FILL_TRANSPARENT_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder().build(false)
        );
    }

    private RenderLayer getHatLineLayer(int alphaValue) {
        return RenderLayer.of(
                "strange_hat_line",
                BUFFER_SIZE,
                false,
                true,
                HAT_LINE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(outlineWidth.get())))
                        .build(false)
        );
    }
}