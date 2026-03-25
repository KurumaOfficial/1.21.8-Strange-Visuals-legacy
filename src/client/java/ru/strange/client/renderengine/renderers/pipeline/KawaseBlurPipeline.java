package ru.strange.client.renderengine.renderers.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
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

public class KawaseBlurPipeline {

    private static final Identifier DOWN_PIPELINE_ID = Identifier.of("strange", "pipeline/kawase_down");
    private static final Identifier DOWN_SHADER = Identifier.of("strange", "core/kawase_down");

    private static final Identifier UP_PIPELINE_ID = Identifier.of("strange", "pipeline/kawase_up");
    private static final Identifier UP_SHADER = Identifier.of("strange", "core/kawase_up");

    private static final RenderPipeline DOWN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(DOWN_PIPELINE_ID)
                    .withVertexShader(DOWN_SHADER)
                    .withFragmentShader(DOWN_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("KawaseData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline UP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(UP_PIPELINE_ID)
                    .withVertexShader(UP_SHADER)
                    .withFragmentShader(UP_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("KawaseData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static final int MAX_ITERATIONS = 8;
    private static final int BUFFER_SIZE = 32;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;

    private GpuTexture[] downTextures;
    private GpuTextureView[] downTextureViews;
    private GpuTexture[] upTextures;
    private GpuTextureView[] upTextureViews;

    private int[] downWidths;
    private int[] downHeights;
    private int[] upWidths;
    private int[] upHeights;

    private GpuTexture finalTexture;
    private GpuTextureView finalTextureView;

    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "strange:kawase_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        this.downTextures = new GpuTexture[MAX_ITERATIONS];
        this.downTextureViews = new GpuTextureView[MAX_ITERATIONS];
        this.upTextures = new GpuTexture[MAX_ITERATIONS];
        this.upTextureViews = new GpuTextureView[MAX_ITERATIONS];
        this.downWidths = new int[MAX_ITERATIONS];
        this.downHeights = new int[MAX_ITERATIONS];
        this.upWidths = new int[MAX_ITERATIONS];
        this.upHeights = new int[MAX_ITERATIONS];

        initialized = true;
    }

    private void ensureFramebuffers(int width, int height) {
        if (width == lastWidth && height == lastHeight) return;

        cleanupFramebuffers();

        finalTexture = RenderSystem.getDevice().createTexture(
                () -> "strange:kawase_final",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        finalTextureView = RenderSystem.getDevice().createTextureView(finalTexture);

        int w = width;
        int h = height;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);

            final int index = i;
            final int fw = w;
            final int fh = h;

            downWidths[i] = fw;
            downHeights[i] = fh;
            upWidths[i] = fw;
            upHeights[i] = fh;

            downTextures[i] = RenderSystem.getDevice().createTexture(
                    () -> "strange:kawase_down_" + index,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8,
                    fw, fh, 1, 1
            );
            downTextureViews[i] = RenderSystem.getDevice().createTextureView(downTextures[i]);

            upTextures[i] = RenderSystem.getDevice().createTexture(
                    () -> "strange:kawase_up_" + index,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8,
                    fw, fh, 1, 1
            );
            upTextureViews[i] = RenderSystem.getDevice().createTextureView(upTextures[i]);
        }

        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupFramebuffers() {
        if (finalTextureView != null) { finalTextureView.close(); finalTextureView = null; }
        if (finalTexture != null) { finalTexture.close(); finalTexture = null; }

        if (downTextureViews != null) {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                if (downTextureViews[i] != null) { downTextureViews[i].close(); downTextureViews[i] = null; }
                if (downTextures[i] != null) { downTextures[i].close(); downTextures[i] = null; }
                if (upTextureViews[i] != null) { upTextureViews[i].close(); upTextureViews[i] = null; }
                if (upTextures[i] != null) { upTextures[i].close(); upTextures[i] = null; }
            }
        }
    }

    public GpuTextureView blur(GpuTexture sourceTexture, GpuTextureView sourceView,
                               int width, int height, int iterations, float offset) {
        if (sourceTexture == null || sourceView == null) return null;

        ensureInitialized();
        ensureFramebuffers(width, height);

        iterations = Math.min(iterations, MAX_ITERATIONS);
        if (iterations < 1) iterations = 1;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        GpuTextureView currentSource = sourceView;
        int currentWidth = width;
        int currentHeight = height;

        for (int i = 0; i < iterations; i++) {
            int targetWidth = downWidths[i];
            int targetHeight = downHeights[i];

            prepareUniformData(currentWidth, currentHeight, offset);

            int size = dataBuffer.remaining();
            if (uniformBuffer == null || uniformBuffer.size() < size) {
                if (uniformBuffer != null) uniformBuffer.close();
                uniformBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "strange:kawase_uniform",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        size
                );
            }
            encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX, 1.0f);

            final int passIndex = i;
            try (RenderPass renderPass = encoder.createRenderPass(
                    () -> "strange:kawase_down_pass_" + passIndex,
                    downTextureViews[i],
                    OptionalInt.empty())) {

                renderPass.setPipeline(DOWN_PIPELINE);
                renderPass.setVertexBuffer(0, dummyVertexBuffer);
                renderPass.bindSampler("Sampler0", currentSource);

                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                renderPass.setUniform("KawaseData", uniformBuffer);

                renderPass.draw(0, 6);
            }

            currentSource = downTextureViews[i];
            currentWidth = targetWidth;
            currentHeight = targetHeight;
        }

        for (int i = iterations - 1; i >= 0; i--) {
            prepareUniformData(currentWidth, currentHeight, offset);
            encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX, 1.0f);

            final int passIndex = i;
            try (RenderPass renderPass = encoder.createRenderPass(
                    () -> "strange:kawase_up_pass_" + passIndex,
                    upTextureViews[i],
                    OptionalInt.empty())) {

                renderPass.setPipeline(UP_PIPELINE);
                renderPass.setVertexBuffer(0, dummyVertexBuffer);
                renderPass.bindSampler("Sampler0", currentSource);

                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                renderPass.setUniform("KawaseData", uniformBuffer);

                renderPass.draw(0, 6);
            }

            currentSource = upTextureViews[i];
            currentWidth = upWidths[i];
            currentHeight = upHeights[i];
        }

        prepareUniformData(currentWidth, currentHeight, offset);
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX, 1.0f);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "strange:kawase_final_pass",
                finalTextureView,
                OptionalInt.empty())) {

            renderPass.setPipeline(UP_PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindSampler("Sampler0", currentSource);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("KawaseData", uniformBuffer);

            renderPass.draw(0, 6);
        }

        return finalTextureView;
    }

    private void prepareUniformData(int width, int height, float offset) {
        dataBuffer.clear();
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(offset);
        dataBuffer.putFloat(0);
        dataBuffer.flip();
    }

    public void close() {
        cleanupFramebuffers();

        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (dataBuffer != null) { MemoryUtil.memFree(dataBuffer); dataBuffer = null; }
        initialized = false;
    }
}
