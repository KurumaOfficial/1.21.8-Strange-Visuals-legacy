package me.x150.renderer.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import me.x150.renderer.mixin.PostEffectPassAccessor;
import me.x150.renderer.mixin.PostEffectProcessorAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.util.Handle;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Shaders {
    private static final Identifier MASK_TARGET = Identifier.of("renderer", "mask");
    private static final String BLUR_CONFIG_UNIFORM = "BlurConfig";
    private static final int UNIFORM_BUFFER_SIZE = 8;

    private static GpuBuffer uniformsBuffer;
    private static PostEffectProcessor gausNoMask;
    private static PostEffectProcessor gausWithMask;
    private static Object shaderLoaderIdentity;
    private static boolean loggedShaderFailure;

    private static GpuBuffer getUniformsBuffer() {
        if (uniformsBuffer == null) {
            uniformsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "renderer blurconfig",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                    UNIFORM_BUFFER_SIZE
            );
        }
        return uniformsBuffer;
    }

    private static @Nullable PostEffectProcessor resolveShader(boolean masked) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getShaderLoader() == null) {
            return null;
        }

        Object currentShaderLoader = client.getShaderLoader();
        if (shaderLoaderIdentity != currentShaderLoader || gausNoMask == null || gausWithMask == null) {
            reloadShaders(client, currentShaderLoader);
        }

        return masked ? gausWithMask : gausNoMask;
    }

    private static void reloadShaders(MinecraftClient client, Object currentShaderLoader) {
        disposeProcessor(gausNoMask);
        disposeProcessor(gausWithMask);
        uniformsBuffer = null;
        gausNoMask = patchUniforms(loadShader(client, "gaussian_no_mask"));
        gausWithMask = patchUniforms(loadShader(client, "gaussian"));
        shaderLoaderIdentity = currentShaderLoader;
        if (gausNoMask != null || gausWithMask != null) {
            loggedShaderFailure = false;
        }
    }

    private static @Nullable PostEffectProcessor loadShader(MinecraftClient client, String shaderName) {
        try {
            return client.getShaderLoader().loadPostEffect(
                    Identifier.of("renderer", shaderName),
                    Set.of(DefaultFramebufferSet.MAIN, MASK_TARGET)
            );
        } catch (RuntimeException exception) {
            logShaderFailureOnce(shaderName, exception);
            return null;
        }
    }

    private static @Nullable PostEffectProcessor patchUniforms(@Nullable PostEffectProcessor processor) {
        if (processor == null) {
            return null;
        }

        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) processor).getPasses();
        GpuBuffer sharedUniformsBuffer = getUniformsBuffer();
        for (PostEffectPass pass : passes) {
            Map<String, GpuBuffer> uniforms = ((PostEffectPassAccessor) pass).getUniformBuffers();
            GpuBuffer currentBuffer = uniforms.get(BLUR_CONFIG_UNIFORM);
            if (currentBuffer != null && currentBuffer != sharedUniformsBuffer) {
                currentBuffer.close();
            }
            uniforms.put(BLUR_CONFIG_UNIFORM, sharedUniformsBuffer);
        }
        return processor;
    }

    private static void disposeProcessor(@Nullable PostEffectProcessor processor) {
        if (processor instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void logShaderFailureOnce(String shaderName, RuntimeException exception) {
        if (loggedShaderFailure) {
            return;
        }

        loggedShaderFailure = true;
        System.err.println("Failed to load blur shader " + shaderName + ": " + exception.getMessage());
    }

    public static void drawBlur(FrameGraphBuilder fgb, int kernelSizePx, float sigma, Framebuffer maskOrNull) {
        PostEffectProcessor processor = resolveShader(maskOrNull != null);
        if (processor == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        Framebuffer mainFramebuffer = client.getFramebuffer();
        int framebufferWidth = mainFramebuffer.textureWidth;
        int framebufferHeight = mainFramebuffer.textureHeight;
        PostEffectProcessor.FramebufferSet framebufferSet = new PostEffectProcessor.FramebufferSet() {
            private Handle<Framebuffer> framebuffer = fgb.createObjectNode("main", mainFramebuffer);
            private Handle<Framebuffer> mask = maskOrNull == null ? null : fgb.createObjectNode("mask", maskOrNull);

            @Override
            public void set(Identifier id, Handle<Framebuffer> framebuffer) {
                if (id.equals(PostEffectProcessor.MAIN)) {
                    this.framebuffer = framebuffer;
                } else if (id.equals(MASK_TARGET)) {
                    this.mask = framebuffer;
                } else {
                    throw new IllegalArgumentException("No target with id " + id);
                }
            }

            @Override
            public @Nullable Handle<Framebuffer> get(Identifier id) {
                if (id.equals(PostEffectProcessor.MAIN)) {
                    return framebuffer;
                }
                if (id.equals(MASK_TARGET)) {
                    return mask;
                }
                return null;
            }
        };

        try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(getUniformsBuffer(), false, true)) {
            Std140Builder std140Builder = Std140Builder.intoBuffer(mappedView.data());
            std140Builder.putFloat(kernelSizePx).putFloat(sigma);
        }

        processor.render(fgb, framebufferWidth, framebufferHeight, framebufferSet);
    }
}
