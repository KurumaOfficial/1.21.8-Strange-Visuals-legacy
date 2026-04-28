package ru.strange.client.renderengine.renderers.util;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import ru.strange.client.renderengine.renderers.pipeline.CosmosCompositePipeline;
import ru.strange.client.renderengine.renderers.pipeline.MaskDiffPipeline;

public final class BlockCosmosRenderer {

    @FunctionalInterface
    public interface WhiteFillDrawer {
        void draw(VertexConsumer buffer);
    }

    private static BlockCosmosRenderer instance;
    private static final int WHITE_FILL_BUFFER_SIZE_BYTES = 1 << 12;

    private static final BlendFunction REPLACE_BLEND = new BlendFunction(
            SourceFactor.ONE, DestFactor.ZERO,
            SourceFactor.ONE, DestFactor.ZERO
    );

    private static final RenderPipeline WHITE_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "block_cosmos_white_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(REPLACE_BLEND)
                    .build()
    );

    private static final RenderLayer WHITE_FILL_LAYER = RenderLayer.of(
            "strange_block_cosmos_white_fill",
            1 << 12,
            false,
            true,
            WHITE_FILL_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

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

    private int lastW;
    private int lastH;
    private final long startTime = System.nanoTime();
    private boolean initialized = false;
    private final BufferAllocator whiteFillBufferAllocator = new BufferAllocator(WHITE_FILL_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate whiteFillConsumers = VertexConsumerProvider.immediate(whiteFillBufferAllocator);

    private BlockCosmosRenderer() {
    }

    public static BlockCosmosRenderer getInstance() {
        if (instance == null) {
            instance = new BlockCosmosRenderer();
        }
        return instance;
    }

    private void ensurePipelines() {
        if (initialized) {
            return;
        }
        cosmosComposite = new CosmosCompositePipeline();
        maskDiff = new MaskDiffPipeline();
        initialized = true;
    }

    private void ensureTextures(int width, int height) {
        ensurePipelines();
        if (width == lastW && height == lastH && sceneTex != null) {
            return;
        }

        cleanupTextures();
        sceneTex = createTex("strange:block_cosmos_scene", TextureFormat.RGBA8, width, height);
        sceneView = RenderSystem.getDevice().createTextureView(sceneTex);
        sceneAfterTex = createTex("strange:block_cosmos_scene_after", TextureFormat.RGBA8, width, height);
        sceneAfterView = RenderSystem.getDevice().createTextureView(sceneAfterTex);
        depthTex = createTex("strange:block_cosmos_depth", TextureFormat.DEPTH32, width, height);
        depthView = RenderSystem.getDevice().createTextureView(depthTex);
        maskTex = createTex("strange:block_cosmos_mask", TextureFormat.RGBA8, width, height);
        maskView = RenderSystem.getDevice().createTextureView(maskTex);
        lastW = width;
        lastH = height;
    }

    public void render(WhiteFillDrawer drawer,
                       float starDensity, float starSpeed,
                       float nebulaIntensity, int nebulaColor, int accentColor,
                       float edgeGlow, int themeIndex, float patternScale, float sparkleScale, float starMix,
                       float pulseAlpha, boolean pulseEffectOnly) {
        render(drawer, starDensity, starSpeed, nebulaIntensity, nebulaColor, accentColor,
                edgeGlow, themeIndex, patternScale, sparkleScale, starMix, pulseAlpha, pulseEffectOnly, 1.0f);
    }

    public void render(WhiteFillDrawer drawer,
                       float starDensity, float starSpeed,
                       float nebulaIntensity, int nebulaColor, int accentColor,
                       float edgeGlow, int themeIndex, float patternScale, float sparkleScale, float starMix,
                       float pulseAlpha, boolean pulseEffectOnly, float maskAlpha) {
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

        CommandEncoder captureEncoder = RenderSystem.getDevice().createCommandEncoder();
        captureEncoder.copyTextureToTexture(framebuffer.getColorAttachment(), sceneTex, 0, 0, 0, 0, 0, width, height);
        if (framebuffer.getDepthAttachment() != null) {
            captureEncoder.copyTextureToTexture(framebuffer.getDepthAttachment(), depthTex, 0, 0, 0, 0, 0, width, height);
        }

        try {
            drawer.draw(whiteFillConsumers.getBuffer(WHITE_FILL_LAYER));
            whiteFillConsumers.draw();
        } finally {
            whiteFillBufferAllocator.clear();
        }

        CommandEncoder restoreEncoder = RenderSystem.getDevice().createCommandEncoder();
        restoreEncoder.copyTextureToTexture(framebuffer.getColorAttachment(), sceneAfterTex, 0, 0, 0, 0, 0, width, height);
        restoreEncoder.copyTextureToTexture(sceneTex, framebuffer.getColorAttachment(), 0, 0, 0, 0, 0, width, height);

        maskDiff.createMask(maskView, sceneView, sceneAfterView, depthView, depthView, width, height);

        float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);
        float resolvedMaskAlpha = Math.max(0.0f, Math.min(1.0f, maskAlpha));
        cosmosComposite.composite(
                framebuffer.getColorAttachmentView(),
                sceneView, sceneAfterView, maskView,
                width, height, time,
                starDensity, starSpeed, nebulaIntensity, nebulaColor, accentColor,
                edgeGlow, themeIndex, patternScale, sparkleScale, starMix, pulseAlpha, pulseEffectOnly,
                0.0f, 0.0f, 0.0f, 0.0f,
                resolvedMaskAlpha, false
        );
    }

    public void render(Matrix4f matrix,
                       double x1, double y1, double z1,
                       double x2, double y2, double z2,
                       float starDensity, float starSpeed,
                       float nebulaIntensity, int nebulaColor, int accentColor,
                       float edgeGlow, int themeIndex, float patternScale, float sparkleScale, float starMix,
                       float pulseAlpha, boolean pulseEffectOnly) {
        render(matrix, x1, y1, z1, x2, y2, z2, starDensity, starSpeed, nebulaIntensity, nebulaColor, accentColor,
                edgeGlow, themeIndex, patternScale, sparkleScale, starMix, pulseAlpha, pulseEffectOnly, 1.0f);
    }

    public void render(Matrix4f matrix,
                       double x1, double y1, double z1,
                       double x2, double y2, double z2,
                       float starDensity, float starSpeed,
                       float nebulaIntensity, int nebulaColor, int accentColor,
                       float edgeGlow, int themeIndex, float patternScale, float sparkleScale, float starMix,
                       float pulseAlpha, boolean pulseEffectOnly, float maskAlpha) {
        render(
                buffer -> addWhiteFaces(buffer, matrix, x1, y1, z1, x2, y2, z2),
                starDensity, starSpeed, nebulaIntensity, nebulaColor, accentColor,
                edgeGlow, themeIndex, patternScale, sparkleScale, starMix, pulseAlpha, pulseEffectOnly, maskAlpha
        );
    }

    private static void addWhiteFaces(VertexConsumer buffer, Matrix4f matrix,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2) {
        float fx1 = (float) x1;
        float fy1 = (float) y1;
        float fz1 = (float) z1;
        float fx2 = (float) x2;
        float fy2 = (float) y2;
        float fz2 = (float) z2;

        buffer.vertex(matrix, fx1, fy1, fz2).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy1, fz2).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy1, fz1).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx1, fy1, fz1).color(255, 255, 255, 255);

        buffer.vertex(matrix, fx1, fy2, fz1).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy2, fz1).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy2, fz2).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx1, fy2, fz2).color(255, 255, 255, 255);

        buffer.vertex(matrix, fx1, fy1, fz2).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx1, fy2, fz2).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy2, fz2).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy1, fz2).color(255, 255, 255, 255);

        buffer.vertex(matrix, fx2, fy1, fz1).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy2, fz1).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx1, fy2, fz1).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx1, fy1, fz1).color(255, 255, 255, 255);

        buffer.vertex(matrix, fx2, fy1, fz2).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy2, fz2).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy2, fz1).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx2, fy1, fz1).color(255, 255, 255, 255);

        buffer.vertex(matrix, fx1, fy1, fz1).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx1, fy2, fz1).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx1, fy2, fz2).color(255, 255, 255, 255);
        buffer.vertex(matrix, fx1, fy1, fz2).color(255, 255, 255, 255);
    }

    private GpuTexture createTex(String name, TextureFormat format, int width, int height) {
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
        lastW = 0;
        lastH = 0;
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