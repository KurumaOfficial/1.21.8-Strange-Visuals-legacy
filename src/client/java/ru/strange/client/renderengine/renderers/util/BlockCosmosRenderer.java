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

/**
 * Applies the same cosmos shader effect as GlassHand to block outline faces.
 * Pipeline: capture scene → draw white fill to FB → capture after → restore scene
 *           → MaskDiff → CosmosComposite.
 */
public final class BlockCosmosRenderer {

    private static BlockCosmosRenderer instance;

    // White fill pipeline — LEQUAL depth test so only visible faces get white
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

    private int lastW, lastH;
    private final long startTime = System.nanoTime();
    private boolean initialized = false;

    private BlockCosmosRenderer() {}

    public static BlockCosmosRenderer getInstance() {
        if (instance == null) instance = new BlockCosmosRenderer();
        return instance;
    }

    private void ensurePipelines() {
        if (initialized) return;
        cosmosComposite = new CosmosCompositePipeline();
        maskDiff = new MaskDiffPipeline();
        initialized = true;
    }

    private void ensureTextures(int w, int h) {
        ensurePipelines();
        if (w == lastW && h == lastH && sceneTex != null) return;
        cleanupTextures();

        sceneTex      = createTex("strange:block_cosmos_scene",       TextureFormat.RGBA8,   w, h);
        sceneView     = RenderSystem.getDevice().createTextureView(sceneTex);
        sceneAfterTex = createTex("strange:block_cosmos_scene_after", TextureFormat.RGBA8,   w, h);
        sceneAfterView = RenderSystem.getDevice().createTextureView(sceneAfterTex);
        depthTex      = createTex("strange:block_cosmos_depth",       TextureFormat.DEPTH32, w, h);
        depthView     = RenderSystem.getDevice().createTextureView(depthTex);
        maskTex       = createTex("strange:block_cosmos_mask",        TextureFormat.RGBA8,   w, h);
        maskView      = RenderSystem.getDevice().createTextureView(maskTex);

        lastW = w;
        lastH = h;
    }

    /**
     * Renders the cosmos shader effect over the block's projected faces.
     *
     * @param matrix      model-view matrix from MatrixStack.peek().getPositionMatrix()
     * @param starDensity Voronoi star density (5–60, same as GlassHand)
     * @param starSpeed   animation speed multiplier
     * @param nebulaIntensity nebula brightness (0–2)
     * @param nebulaColor ARGB packed primary tint colour
     * @param accentColor ARGB packed accent tint colour
     * @param edgeGlow    silhouette edge glow strength
     */
    public void render(Matrix4f matrix,
                       double x1, double y1, double z1,
                       double x2, double y2, double z2,
                       float starDensity, float starSpeed,
                       float nebulaIntensity, int nebulaColor, int accentColor,
                       float edgeGlow, int themeIndex, float patternScale, float sparkleScale, float starMix,
                       float pulseAlpha, boolean pulseEffectOnly) {

        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        int w = fb.textureWidth;
        int h = fb.textureHeight;
        ensureTextures(w, h);

        // 1. Capture scene + depth BEFORE we touch the FB
        {
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            enc.copyTextureToTexture(fb.getColorAttachment(), sceneTex, 0, 0, 0, 0, 0, w, h);
            if (fb.getDepthAttachment() != null) {
                enc.copyTextureToTexture(fb.getDepthAttachment(), depthTex, 0, 0, 0, 0, 0, w, h);
            }
        }

        // 2. Draw solid white fill of block faces ONTO the FB — creates a sharp mask shape.
        //    Uses a dedicated temporary immediate so the flush happens right here.
        try (BufferAllocator alloc = new BufferAllocator(1 << 12)) {
            VertexConsumerProvider.Immediate tmp = VertexConsumerProvider.immediate(alloc);
            addWhiteFaces(tmp.getBuffer(WHITE_FILL_LAYER), matrix, x1, y1, z1, x2, y2, z2);
            tmp.draw();
        }

        // 3. Capture scene AFTER white fill (has bright white block faces on game scene)
        {
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            enc.copyTextureToTexture(fb.getColorAttachment(), sceneAfterTex, 0, 0, 0, 0, 0, w, h);
        }

        // 4. Restore FB to the original scene (undo the white fill)
        {
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            enc.copyTextureToTexture(sceneTex, fb.getColorAttachment(), 0, 0, 0, 0, 0, w, h);
        }

        // 5. Generate mask: MaskDiff compares color before vs after — white faces create mask
        //    Pass same depthView for both since we didn't change depth (pure color diff for mask)
        maskDiff.createMask(maskView, sceneView, sceneAfterView, depthView, depthView, w, h);

        // 6. Apply cosmos composite (identical to GlassHand cosmos mode)
        float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);
        cosmosComposite.composite(
                fb.getColorAttachmentView(),
                sceneView, sceneView, maskView,
                w, h, time,
                starDensity, starSpeed, nebulaIntensity, nebulaColor, accentColor,
                edgeGlow, themeIndex, patternScale, sparkleScale, starMix, pulseAlpha, pulseEffectOnly
        );
    }

    // Build 24 white vertices for the 6 faces of the AABB block
    private static void addWhiteFaces(VertexConsumer buf, Matrix4f m,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2) {
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1;
        float fx2 = (float) x2, fy2 = (float) y2, fz2 = (float) z2;

        // bottom (y1)
        buf.vertex(m, fx1, fy1, fz2).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy1, fz2).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy1, fz1).color(255, 255, 255, 255);
        buf.vertex(m, fx1, fy1, fz1).color(255, 255, 255, 255);
        // top (y2)
        buf.vertex(m, fx1, fy2, fz1).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy2, fz1).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy2, fz2).color(255, 255, 255, 255);
        buf.vertex(m, fx1, fy2, fz2).color(255, 255, 255, 255);
        // front (z2)
        buf.vertex(m, fx1, fy1, fz2).color(255, 255, 255, 255);
        buf.vertex(m, fx1, fy2, fz2).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy2, fz2).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy1, fz2).color(255, 255, 255, 255);
        // back (z1)
        buf.vertex(m, fx2, fy1, fz1).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy2, fz1).color(255, 255, 255, 255);
        buf.vertex(m, fx1, fy2, fz1).color(255, 255, 255, 255);
        buf.vertex(m, fx1, fy1, fz1).color(255, 255, 255, 255);
        // right (x2)
        buf.vertex(m, fx2, fy1, fz2).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy2, fz2).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy2, fz1).color(255, 255, 255, 255);
        buf.vertex(m, fx2, fy1, fz1).color(255, 255, 255, 255);
        // left (x1)
        buf.vertex(m, fx1, fy1, fz1).color(255, 255, 255, 255);
        buf.vertex(m, fx1, fy2, fz1).color(255, 255, 255, 255);
        buf.vertex(m, fx1, fy2, fz2).color(255, 255, 255, 255);
        buf.vertex(m, fx1, fy1, fz2).color(255, 255, 255, 255);
    }

    private GpuTexture createTex(String name, TextureFormat format, int w, int h) {
        return RenderSystem.getDevice().createTexture(
                () -> name,
                GpuTexture.USAGE_COPY_SRC
                        | GpuTexture.USAGE_COPY_DST
                        | GpuTexture.USAGE_TEXTURE_BINDING
                        | GpuTexture.USAGE_RENDER_ATTACHMENT,
                format, w, h, 1, 1
        );
    }

    private void cleanupTextures() {
        if (sceneView != null)      { sceneView.close();      sceneView = null; }
        if (sceneTex != null)       { sceneTex.close();       sceneTex = null; }
        if (sceneAfterView != null) { sceneAfterView.close(); sceneAfterView = null; }
        if (sceneAfterTex != null)  { sceneAfterTex.close();  sceneAfterTex = null; }
        if (depthView != null)      { depthView.close();      depthView = null; }
        if (depthTex != null)       { depthTex.close();       depthTex = null; }
        if (maskView != null)       { maskView.close();       maskView = null; }
        if (maskTex != null)        { maskTex.close();        maskTex = null; }
        lastW = 0;
        lastH = 0;
    }

    public void close() {
        cleanupTextures();
        if (cosmosComposite != null) { cosmosComposite.close(); cosmosComposite = null; }
        if (maskDiff != null)        { maskDiff.close();        maskDiff = null; }
        initialized = false;
    }
}
