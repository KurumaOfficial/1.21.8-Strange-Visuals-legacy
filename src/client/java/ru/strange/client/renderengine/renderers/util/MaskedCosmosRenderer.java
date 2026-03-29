package ru.strange.client.renderengine.renderers.util;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import ru.strange.client.renderengine.renderers.pipeline.CosmosCompositePipeline;
import ru.strange.client.renderengine.renderers.pipeline.MaskDiffPipeline;

public final class MaskedCosmosRenderer {
    private static final int MASK_DRAW_BUFFER_SIZE_BYTES = 1 << 18;

    @FunctionalInterface
    public interface MaskDrawer {
        void draw(VertexConsumerProvider.Immediate immediate);
    }

    private static MaskedCosmosRenderer instance;

    private CosmosCompositePipeline cosmosComposite;
    private MaskDiffPipeline maskDiff;

    private GpuTexture sceneTex;
    private GpuTextureView sceneView;
    private GpuTexture sceneAfterTex;
    private GpuTextureView sceneAfterView;
    private GpuTexture depthTex;
    private GpuTextureView depthView;
    private GpuTexture maskTex;
    private GpuTextureView maskView;

    private int lastWidth;
    private int lastHeight;
    private long startTime = System.nanoTime();
    private boolean initialized;
    private final BufferAllocator maskDrawBufferAllocator = new BufferAllocator(MASK_DRAW_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate maskRenderConsumers = VertexConsumerProvider.immediate(maskDrawBufferAllocator);

    private MaskedCosmosRenderer() {
    }

    public static MaskedCosmosRenderer getInstance() {
        if (instance == null) {
            instance = new MaskedCosmosRenderer();
        }
        return instance;
    }

    public void render(MaskDrawer drawer,
                       float starDensity, float starSpeed,
                       float nebulaIntensity, int nebulaColor, int accentColor,
                       float edgeGlow, int themeIndex, float patternScale, float sparkleScale, float starMix,
                       float pulseAlpha, boolean pulseEffectOnly) {
        if (drawer == null) {
            return;
        }

        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) {
            return;
        }

        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        ensureTextures(width, height);

        captureScene(framebuffer, width, height);

        try {
            drawer.draw(maskRenderConsumers);
            maskRenderConsumers.draw();
        } finally {
            maskDrawBufferAllocator.clear();
        }

        restoreSceneState(framebuffer, width, height);

        maskDiff.createMask(maskView, sceneView, sceneAfterView, depthView, depthView, width, height);

        float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);
        cosmosComposite.composite(
                framebuffer.getColorAttachmentView(),
                sceneView, sceneAfterView, maskView,
                width, height, time,
                starDensity, starSpeed, nebulaIntensity, nebulaColor, accentColor,
                edgeGlow, themeIndex, patternScale, sparkleScale, starMix, pulseAlpha, pulseEffectOnly
        );
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        cosmosComposite = new CosmosCompositePipeline();
        maskDiff = new MaskDiffPipeline();
        initialized = true;
    }

    private void ensureTextures(int width, int height) {
        ensureInitialized();
        if (width == lastWidth && height == lastHeight && sceneTex != null) {
            return;
        }

        cleanupTextures();
        sceneTex = createTexture("strange:masked_scene", TextureFormat.RGBA8, width, height);
        sceneView = RenderSystem.getDevice().createTextureView(sceneTex);
        sceneAfterTex = createTexture("strange:masked_scene_after", TextureFormat.RGBA8, width, height);
        sceneAfterView = RenderSystem.getDevice().createTextureView(sceneAfterTex);
        depthTex = createTexture("strange:masked_depth", TextureFormat.DEPTH32, width, height);
        depthView = RenderSystem.getDevice().createTextureView(depthTex);
        maskTex = createTexture("strange:masked_mask", TextureFormat.RGBA8, width, height);
        maskView = RenderSystem.getDevice().createTextureView(maskTex);
        lastWidth = width;
        lastHeight = height;
    }

    private void captureScene(Framebuffer framebuffer, int width, int height) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(framebuffer.getColorAttachment(), sceneTex, 0, 0, 0, 0, 0, width, height);
        if (framebuffer.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(framebuffer.getDepthAttachment(), depthTex, 0, 0, 0, 0, 0, width, height);
        }
    }

    private void restoreSceneState(Framebuffer framebuffer, int width, int height) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(framebuffer.getColorAttachment(), sceneAfterTex, 0, 0, 0, 0, 0, width, height);
        encoder.copyTextureToTexture(sceneTex, framebuffer.getColorAttachment(), 0, 0, 0, 0, 0, width, height);
    }

    private GpuTexture createTexture(String name, TextureFormat format, int width, int height) {
        return RenderSystem.getDevice().createTexture(
                () -> name,
                GpuTexture.USAGE_COPY_SRC
                        | GpuTexture.USAGE_COPY_DST
                        | GpuTexture.USAGE_TEXTURE_BINDING
                        | GpuTexture.USAGE_RENDER_ATTACHMENT,
                format, width, height, 1, 1
        );
    }

    private void cleanupTextures() {
        if (sceneView != null) {
            sceneView.close();
            sceneView = null;
        }
        if (sceneTex != null) {
            sceneTex.close();
            sceneTex = null;
        }
        if (sceneAfterView != null) {
            sceneAfterView.close();
            sceneAfterView = null;
        }
        if (sceneAfterTex != null) {
            sceneAfterTex.close();
            sceneAfterTex = null;
        }
        if (depthView != null) {
            depthView.close();
            depthView = null;
        }
        if (depthTex != null) {
            depthTex.close();
            depthTex = null;
        }
        if (maskView != null) {
            maskView.close();
            maskView = null;
        }
        if (maskTex != null) {
            maskTex.close();
            maskTex = null;
        }
        lastWidth = 0;
        lastHeight = 0;
    }

    public void close() {
        cleanupTextures();
        if (cosmosComposite != null) {
            cosmosComposite.close();
            cosmosComposite = null;
        }
        if (maskDiff != null) {
            maskDiff.close();
            maskDiff = null;
        }
        initialized = false;
    }
}
