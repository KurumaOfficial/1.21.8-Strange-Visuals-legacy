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

public final class GpsHudMaskPipeline {
    private static final Identifier PIPELINE_ID = Identifier.of("strange", "pipeline/gps_hud_mask");
    private static final Identifier SHADER = Identifier.of("strange", "core/gps_hud_mask");
    private static final int BUFFER_SIZE = 48;

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
                    .withUniform("HudMaskData", UniformType.UNIFORM_BUFFER)
                    .withSampler("MaskTextureSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized;

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "strange:gps_hud_mask_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    public void render(GpuTextureView targetView, GpuTextureView maskTextureView,
                       int screenWidth, int screenHeight,
                       float x, float y, float width, float height, float alpha) {
        if (targetView == null || maskTextureView == null || width <= 0.0f || height <= 0.0f || alpha <= 0.0f) {
            return;
        }

        ensureInitialized();

        prepareUniformData(screenWidth, screenHeight, x, y, width, height, alpha);
        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "strange:gps_hud_mask_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX, 1.0f);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "strange:gps_hud_mask_pass",
                targetView,
                OptionalInt.of(0x00000000))) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindSampler("MaskTextureSampler", maskTextureView);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("HudMaskData", uniformBuffer);

            renderPass.draw(0, 6);
        }
    }

    private void prepareUniformData(int screenWidth, int screenHeight,
                                    float x, float y, float width, float height, float alpha) {
        dataBuffer.clear();

        dataBuffer.putFloat(screenWidth);
        dataBuffer.putFloat(screenHeight);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);

        dataBuffer.putFloat(x);
        dataBuffer.putFloat(y);
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);

        dataBuffer.putFloat(alpha);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);

        dataBuffer.flip();
    }

    public void close() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }
}
