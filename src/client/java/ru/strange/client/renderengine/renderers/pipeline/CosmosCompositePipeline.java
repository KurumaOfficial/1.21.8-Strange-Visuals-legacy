package ru.strange.client.renderengine.renderers.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public class CosmosCompositePipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("strange", "pipeline/cosmos_composite");
    private static final Identifier SHADER = Identifier.of("strange", "core/cosmos_composite");

    private static final BlendFunction REPLACE_BLEND = new BlendFunction(
            SourceFactor.ONE, DestFactor.ZERO,
            SourceFactor.ONE, DestFactor.ZERO
    );

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(SHADER)
                    .withFragmentShader(SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("CosmosData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withSampler("SceneAfterSampler")
                    .withSampler("MaskSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int BUFFER_SIZE = 112;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "strange:cosmos_composite_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    public void composite(GpuTextureView targetView, GpuTextureView sceneView,
                          GpuTextureView sceneAfterView,
                          GpuTextureView maskView,
                          int width, int height, float time,
                          float starDensity, float starSpeed, float nebulaIntensity,
                          int nebulaColor, int accentColor, float edgeGlowIntensity,
                          int themeIndex, float patternScale, float sparkleScale, float starMix,
                          float pulseAlpha, boolean pulseEffectOnly) {
        composite(
                targetView,
                sceneView,
                sceneAfterView,
                maskView,
                width,
                height,
                time,
                starDensity,
                starSpeed,
                nebulaIntensity,
                nebulaColor,
                accentColor,
                edgeGlowIntensity,
                themeIndex,
                patternScale,
                sparkleScale,
                starMix,
                pulseAlpha,
                pulseEffectOnly,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f,
                false
        );
    }

    public void composite(GpuTextureView targetView, GpuTextureView sceneView,
                          GpuTextureView sceneAfterView,
                          GpuTextureView maskView,
                          int width, int height, float time,
                          float starDensity, float starSpeed, float nebulaIntensity,
                          int nebulaColor, int accentColor, float edgeGlowIntensity,
                          int themeIndex, float patternScale, float sparkleScale, float starMix,
                          float pulseAlpha, boolean pulseEffectOnly,
                          float maskRectX, float maskRectY, float maskRectWidth, float maskRectHeight,
                          float directMaskAlpha, boolean directMaskRect) {

        ensureInitialized();

        prepareUniformData(width, height, time, starDensity, starSpeed, nebulaIntensity,
                nebulaColor, accentColor, edgeGlowIntensity, themeIndex, patternScale, sparkleScale, starMix,
                pulseAlpha, pulseEffectOnly, maskRectX, maskRectY, maskRectWidth, maskRectHeight, directMaskAlpha, directMaskRect);

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "strange:cosmos_composite_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX, 1.0f);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "strange:cosmos_composite_pass",
                targetView,
                OptionalInt.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindSampler("SceneSampler", sceneView);
            renderPass.bindSampler("SceneAfterSampler", sceneAfterView);
            renderPass.bindSampler("MaskSampler", maskView);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("CosmosData", uniformBuffer);

            renderPass.draw(0, 6);
        }
    }

    private void prepareUniformData(int width, int height, float time,
                                    float starDensity, float starSpeed, float nebulaIntensity,
                                    int nebulaColor, int accentColor, float edgeGlowIntensity,
                                    int themeIndex, float patternScale, float sparkleScale, float starMix,
                                    float pulseAlpha, boolean pulseEffectOnly,
                                    float maskRectX, float maskRectY, float maskRectWidth, float maskRectHeight,
                                    float directMaskAlpha, boolean directMaskRect) {
        dataBuffer.clear();

        // screenParams: width, height, time, starDensity
        dataBuffer.putFloat((float) width);
        dataBuffer.putFloat((float) height);
        dataBuffer.putFloat(time);
        dataBuffer.putFloat(starDensity);

        // cosmosParams: starSpeed, nebulaIntensity, edgeGlow, themeIndex
        dataBuffer.putFloat(starSpeed);
        dataBuffer.putFloat(nebulaIntensity);
        dataBuffer.putFloat(edgeGlowIntensity);
        dataBuffer.putFloat((float) themeIndex);

        // nebulaColor: r, g, b, a
        dataBuffer.putFloat(((nebulaColor >> 16) & 0xFF) / 255.0f);
        dataBuffer.putFloat(((nebulaColor >> 8) & 0xFF) / 255.0f);
        dataBuffer.putFloat((nebulaColor & 0xFF) / 255.0f);
        dataBuffer.putFloat(((nebulaColor >> 24) & 0xFF) / 255.0f);

        // accentColor
        dataBuffer.putFloat(((accentColor >> 16) & 0xFF) / 255.0f);
        dataBuffer.putFloat(((accentColor >> 8) & 0xFF) / 255.0f);
        dataBuffer.putFloat((accentColor & 0xFF) / 255.0f);
        dataBuffer.putFloat(((accentColor >> 24) & 0xFF) / 255.0f);

        // themeParams
        dataBuffer.putFloat(patternScale);
        dataBuffer.putFloat(sparkleScale);
        dataBuffer.putFloat(starMix);
        dataBuffer.putFloat(pulseAlpha);

        // pad
        dataBuffer.putFloat(pulseEffectOnly ? 1.0f : 0.0f);
        dataBuffer.putFloat(directMaskAlpha);
        dataBuffer.putFloat(directMaskRect ? 1.0f : 0.0f);
        dataBuffer.putFloat(0.0f);

        // maskRect
        dataBuffer.putFloat(maskRectX);
        dataBuffer.putFloat(maskRectY);
        dataBuffer.putFloat(maskRectWidth);
        dataBuffer.putFloat(maskRectHeight);

        dataBuffer.flip();
    }

    public void close() {
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (dataBuffer != null) { MemoryUtil.memFree(dataBuffer); dataBuffer = null; }
        initialized = false;
    }
}
