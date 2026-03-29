package ru.strange.client.renderengine.renderers.pipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

public final class PipelineRegistry {

    private static VertexFormatElement UV1_ELEMENT;
    private static VertexFormatElement RADIUS_XY_ELEMENT;
    private static VertexFormatElement RADIUS_ZW_ELEMENT;
    private static VertexFormatElement EXTRA_DATA_1_ELEMENT;
    private static VertexFormatElement EXTRA_DATA_2_ELEMENT;
    private static VertexFormatElement SCREEN_UV_ELEMENT;

    private static int getNextVFId() {
        for(int i = 0; i < VertexFormatElement.MAX_COUNT; i++) {
            if (VertexFormatElement.byId(i) == null) return i;
        }
        throw new IllegalStateException("No more free VertexFormatElement slots");
    }

    public static RenderPipeline RECTANGLE_PIPELINE;
    public static RenderPipeline BORDER_PIPELINE;
    public static RenderPipeline TEXTURE_PIPELINE;
    public static RenderPipeline TEXTURE_OPAQUE_PIPELINE;
    public static RenderPipeline TEXTURE_OPAQUE_SCREEN_PIPELINE;
    public static RenderPipeline BLUR_PIPELINE;
    public static RenderPipeline SHADOW_PIPELINE;
    public static RenderPipeline LIQUID_GLASS_PIPELINE;

    public static void init() {
        // Регистрируем элементы
        UV1_ELEMENT = VertexFormatElement.register(getNextVFId(), 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
        RADIUS_XY_ELEMENT = VertexFormatElement.register(getNextVFId(), 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
        RADIUS_ZW_ELEMENT = VertexFormatElement.register(getNextVFId(), 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
        EXTRA_DATA_1_ELEMENT = VertexFormatElement.register(getNextVFId(), 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
        EXTRA_DATA_2_ELEMENT = VertexFormatElement.register(getNextVFId(), 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
        SCREEN_UV_ELEMENT = VertexFormatElement.register(getNextVFId(), 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);

        // Rectangle Pipeline
        RECTANGLE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of("strange", "pipeline/rectangle"))
                .withVertexShader(Identifier.of("strange", "core/rectangle"))
                .withFragmentShader(Identifier.of("strange", "core/rectangle"))
                .withVertexFormat(VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV1", UV1_ELEMENT)
                        .add("RadiusXY", RADIUS_XY_ELEMENT)
                        .add("RadiusZW", RADIUS_ZW_ELEMENT)
                        .add("ExtraData1", EXTRA_DATA_1_ELEMENT)
                        .build(), VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build()
        );

        // Border Pipeline
        BORDER_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of("strange", "pipeline/border"))
                .withVertexShader(Identifier.of("strange", "core/border"))
                .withFragmentShader(Identifier.of("strange", "core/border"))
                .withVertexFormat(VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV1", UV1_ELEMENT)
                        .add("RadiusXY", RADIUS_XY_ELEMENT)
                        .add("RadiusZW", RADIUS_ZW_ELEMENT)
                        .add("BorderData1", EXTRA_DATA_1_ELEMENT)
                        .add("BorderData2", EXTRA_DATA_2_ELEMENT)
                        .build(), VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build()
        );

        // Texture Pipeline
        TEXTURE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of("strange", "pipeline/texture"))
                .withVertexShader(Identifier.of("strange", "core/texture"))
                .withFragmentShader(Identifier.of("strange", "core/texture"))
                .withVertexFormat(VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV1", UV1_ELEMENT)
                        .add("RadiusXY", RADIUS_XY_ELEMENT)
                        .add("RadiusZW", RADIUS_ZW_ELEMENT)
                        .add("ExtraData1", EXTRA_DATA_1_ELEMENT)
                        .build(), VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withSampler("Sampler0")
                .build()
        );

        // Texture Opaque Pipeline (force texture alpha = 1.0)
        TEXTURE_OPAQUE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of("strange", "pipeline/texture_opaque"))
                .withVertexShader(Identifier.of("strange", "core/texture"))
                .withFragmentShader(Identifier.of("strange", "core/texture_opaque"))
                .withVertexFormat(VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV1", UV1_ELEMENT)
                        .add("RadiusXY", RADIUS_XY_ELEMENT)
                        .add("RadiusZW", RADIUS_ZW_ELEMENT)
                        .add("ExtraData1", EXTRA_DATA_1_ELEMENT)
                        .build(), VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withSampler("Sampler0")
                .build()
        );

        // Texture Opaque Screen Pipeline (screen-space UV, force alpha = 1.0)
        TEXTURE_OPAQUE_SCREEN_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of("strange", "pipeline/texture_opaque_screen"))
                .withVertexShader(Identifier.of("strange", "core/texture_screen"))
                .withFragmentShader(Identifier.of("strange", "core/texture_opaque_screen"))
                .withVertexFormat(VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV1", UV1_ELEMENT)
                        .add("RadiusXY", RADIUS_XY_ELEMENT)
                        .add("RadiusZW", RADIUS_ZW_ELEMENT)
                        .add("ExtraData1", EXTRA_DATA_1_ELEMENT)
                        .add("ExtraData2", EXTRA_DATA_2_ELEMENT)
                        .build(), VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withSampler("Sampler0")
                .build()
        );

        // Blur Pipeline
        BLUR_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of("strange", "pipeline/blur"))
                .withVertexShader(Identifier.of("strange", "core/blur"))
                .withFragmentShader(Identifier.of("strange", "core/blur"))
                .withVertexFormat(VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV1", UV1_ELEMENT)
                        .add("RadiusXY", RADIUS_XY_ELEMENT)
                        .add("RadiusZW", RADIUS_ZW_ELEMENT)
                        .add("BlurData", EXTRA_DATA_1_ELEMENT)
                        .build(), VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withSampler("Sampler0")
                .build()
        );

        // Shadow Pipeline
        SHADOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of("strange", "pipeline/shadow"))
                .withVertexShader(Identifier.of("strange", "core/shadow"))
                .withFragmentShader(Identifier.of("strange", "core/shadow"))
                .withVertexFormat(VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV1", UV1_ELEMENT)
                        .add("RadiusXY", RADIUS_XY_ELEMENT)
                        .add("RadiusZW", RADIUS_ZW_ELEMENT)
                        .add("ShadowData", EXTRA_DATA_1_ELEMENT)
                        .build(), VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build()
        );

        // Liquid Glass Pipeline
        LIQUID_GLASS_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of("strange", "pipeline/liquidglass"))
                .withVertexShader(Identifier.of("strange", "core/liquidglass"))
                .withFragmentShader(Identifier.of("strange", "core/liquidglass"))
                .withVertexFormat(VertexFormat.builder()
                        .add("Position", VertexFormatElement.POSITION)
                        .add("UV0", VertexFormatElement.UV0)
                        .add("Color", VertexFormatElement.COLOR)
                        .add("UV1", UV1_ELEMENT)
                        .add("RadiusXY", RADIUS_XY_ELEMENT)
                        .add("RadiusZW", RADIUS_ZW_ELEMENT)
                        .add("ExtraData1", EXTRA_DATA_1_ELEMENT)
                        .add("ExtraData2", EXTRA_DATA_2_ELEMENT)
                        .add("ScreenUV", SCREEN_UV_ELEMENT)
                        .add("CenterUV", SCREEN_UV_ELEMENT)  // Используем тот же элемент для CenterUV
                        .build(), VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withSampler("Sampler0")
                .build()
        );
    }

    private PipelineRegistry() {}
}