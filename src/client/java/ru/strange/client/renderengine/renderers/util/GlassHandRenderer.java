package ru.strange.client.renderengine.renderers.util;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import ru.strange.client.renderengine.renderers.pipeline.CosmosCompositePipeline;
import ru.strange.client.renderengine.renderers.pipeline.GlassCompositePipeline;
import ru.strange.client.renderengine.renderers.pipeline.KawaseBlurPipeline;
import ru.strange.client.renderengine.renderers.pipeline.MaskDiffPipeline;

/**
 * GlassHand renderer using modern GPU pipeline API.
 * Captures scene before/after hands, diffs to create mask,
 * blurs background, composites glass effect.
 */
public final class GlassHandRenderer {

    private static GlassHandRenderer instance;

    private final MinecraftClient client;
    private KawaseBlurPipeline kawaseBlur;
    private GlassCompositePipeline glassComposite;
    private CosmosCompositePipeline cosmosComposite;
    private MaskDiffPipeline maskDiff;

    private GpuTexture sceneBeforeTexture;
    private GpuTextureView sceneBeforeTextureView;
    private GpuTexture sceneAfterTexture;
    private GpuTextureView sceneAfterTextureView;
    private GpuTexture depthBeforeTexture;
    private GpuTextureView depthBeforeTextureView;
    private GpuTexture depthAfterTexture;
    private GpuTextureView depthAfterTextureView;
    private GpuTexture maskTexture;
    private GpuTextureView maskTextureView;

    private int lastWidth = 0;
    private int lastHeight = 0;

    private boolean capturing = false;
    private boolean enabled = false;
    private boolean initialized = false;

    private float blurRadius = 2.5f;
    private int blurIterations = 3;
    private float saturation = 1.0f;
    private boolean reflect = true;
    private int tintColor = 0x00000000;
    private float tintIntensity = 0.1f;
    private float edgeGlowIntensity = 0.3f;
    private int glowColor = 0xFFFFFFFF;
    private float glowIntensity = 0.0f;

    // 0 = Glass, 1 = Cosmos
    private int mode = 0;
    private float cosmosStarDensity = 40.0f;
    private float cosmosStarSpeed = 0.3f;
    private float cosmosNebulaIntensity = 0.8f;
    private int cosmosNebulaColor = 0xFF6A0DAD;
    private int cosmosAccentColor = 0xFFB45CFF;
    private float cosmosEdgeGlowIntensity = 0.5f;
    private int cosmosThemeIndex = 0;
    private float cosmosPatternScale = 1.0f;
    private float cosmosSparkleScale = 1.0f;
    private float cosmosStarMix = 1.0f;
    private float cosmosPulseAlpha = 1.0f;
    private boolean cosmosPulseEffectOnly = false;
    private long startTime = System.nanoTime();

    public GlassHandRenderer() {
        this.client = MinecraftClient.getInstance();
        instance = this;
    }

    public static GlassHandRenderer getInstance() {
        if (instance == null) {
            instance = new GlassHandRenderer();
        }
        return instance;
    }

    private void ensureInitialized() {
        if (initialized) return;

        if (kawaseBlur != null) kawaseBlur.close();
        if (glassComposite != null) glassComposite.close();
        if (cosmosComposite != null) cosmosComposite.close();
        if (maskDiff != null) maskDiff.close();

        this.kawaseBlur = new KawaseBlurPipeline();
        this.glassComposite = new GlassCompositePipeline();
        this.cosmosComposite = new CosmosCompositePipeline();
        this.maskDiff = new MaskDiffPipeline();

        lastWidth = 0;
        lastHeight = 0;

        initialized = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            ensureInitialized();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setBlurRadius(float radius) {
        this.blurRadius = radius;
    }

    public void setBlurIterations(int iterations) {
        this.blurIterations = Math.max(1, Math.min(8, iterations));
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
    }

    public void setReflect(boolean reflect) {
        this.reflect = reflect;
    }

    public void setTintColor(int color) {
        this.tintColor = color;
    }

    public void setTintIntensity(float intensity) {
        this.tintIntensity = intensity;
    }

    public void setEdgeGlowIntensity(float intensity) {
        this.edgeGlowIntensity = intensity;
    }

    public void setGlowColor(int color) {
        this.glowColor = color;
    }

    public void setGlowIntensity(float intensity) {
        this.glowIntensity = intensity;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getMode() {
        return mode;
    }

    public void setCosmosStarDensity(float density) {
        this.cosmosStarDensity = density;
    }

    public void setCosmosStarSpeed(float speed) {
        this.cosmosStarSpeed = speed;
    }

    public void setCosmosNebulaIntensity(float intensity) {
        this.cosmosNebulaIntensity = intensity;
    }

    public void setCosmosNebulaColor(int color) {
        this.cosmosNebulaColor = color;
    }

    public void setCosmosAccentColor(int color) {
        this.cosmosAccentColor = color;
    }

    public void setCosmosEdgeGlowIntensity(float intensity) {
        this.cosmosEdgeGlowIntensity = intensity;
    }

    public void setCosmosThemeIndex(int themeIndex) {
        this.cosmosThemeIndex = Math.max(0, themeIndex);
    }

    public void setCosmosPatternScale(float patternScale) {
        this.cosmosPatternScale = Math.max(0.1f, patternScale);
    }

    public void setCosmosSparkleScale(float sparkleScale) {
        this.cosmosSparkleScale = Math.max(0.0f, sparkleScale);
    }

    public void setCosmosStarMix(float starMix) {
        this.cosmosStarMix = Math.max(0.0f, Math.min(1.0f, starMix));
    }

    public void setCosmosPulseAlpha(float pulseAlpha) {
        this.cosmosPulseAlpha = Math.max(0.0f, pulseAlpha);
    }

    public void setCosmosPulseEffectOnly(boolean pulseEffectOnly) {
        this.cosmosPulseEffectOnly = pulseEffectOnly;
    }

    private void ensureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneBeforeTexture != null) return;

        cleanupTextures();

        sceneBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "strange:glass_scene_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        sceneBeforeTextureView = RenderSystem.getDevice().createTextureView(sceneBeforeTexture);

        sceneAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "strange:glass_scene_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        sceneAfterTextureView = RenderSystem.getDevice().createTextureView(sceneAfterTexture);

        depthBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "strange:glass_depth_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32,
                width, height, 1, 1
        );
        depthBeforeTextureView = RenderSystem.getDevice().createTextureView(depthBeforeTexture);

        depthAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "strange:glass_depth_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32,
                width, height, 1, 1
        );
        depthAfterTextureView = RenderSystem.getDevice().createTextureView(depthAfterTexture);

        maskTexture = RenderSystem.getDevice().createTexture(
                () -> "strange:glass_mask",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        maskTextureView = RenderSystem.getDevice().createTextureView(maskTexture);

        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupTextures() {
        if (sceneBeforeTextureView != null) { sceneBeforeTextureView.close(); sceneBeforeTextureView = null; }
        if (sceneBeforeTexture != null) { sceneBeforeTexture.close(); sceneBeforeTexture = null; }
        if (sceneAfterTextureView != null) { sceneAfterTextureView.close(); sceneAfterTextureView = null; }
        if (sceneAfterTexture != null) { sceneAfterTexture.close(); sceneAfterTexture = null; }
        if (depthBeforeTextureView != null) { depthBeforeTextureView.close(); depthBeforeTextureView = null; }
        if (depthBeforeTexture != null) { depthBeforeTexture.close(); depthBeforeTexture = null; }
        if (depthAfterTextureView != null) { depthAfterTextureView.close(); depthAfterTextureView = null; }
        if (depthAfterTexture != null) { depthAfterTexture.close(); depthAfterTexture = null; }
        if (maskTextureView != null) { maskTextureView.close(); maskTextureView = null; }
        if (maskTexture != null) { maskTexture.close(); maskTexture = null; }
    }

    public void captureSceneBeforeHands() {
        if (!enabled) return;

        ensureInitialized();

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        int width = fb.textureWidth;
        int height = fb.textureHeight;

        ensureTextures(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        encoder.copyTextureToTexture(
                fb.getColorAttachment(),
                sceneBeforeTexture,
                0, 0, 0, 0, 0,
                width, height
        );

        if (fb.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(
                    fb.getDepthAttachment(),
                    depthBeforeTexture,
                    0, 0, 0, 0, 0,
                    width, height
            );
        }

        capturing = true;
    }

    public void captureSceneAfterHands() {
        if (!enabled || !capturing) return;

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        encoder.copyTextureToTexture(
                fb.getColorAttachment(),
                sceneAfterTexture,
                0, 0, 0, 0, 0,
                lastWidth, lastHeight
        );

        if (fb.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(
                    fb.getDepthAttachment(),
                    depthAfterTexture,
                    0, 0, 0, 0, 0,
                    lastWidth, lastHeight
            );
        }
    }

    public void renderGlassEffect() {
        if (!enabled || !capturing) return;

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) {
            capturing = false;
            return;
        }

        maskDiff.createMask(
                maskTextureView,
                sceneBeforeTextureView,
                sceneAfterTextureView,
                depthBeforeTextureView,
                depthAfterTextureView,
                lastWidth, lastHeight
        );

        if (mode == 1) {
            // Cosmos mode
            float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);

            cosmosComposite.composite(
                    fb.getColorAttachmentView(),
                    sceneBeforeTextureView,
                    sceneAfterTextureView,
                    maskTextureView,
                    lastWidth, lastHeight,
                    time,
                    cosmosStarDensity,
                    cosmosStarSpeed,
                    cosmosNebulaIntensity,
                    cosmosNebulaColor,
                    cosmosAccentColor,
                    cosmosEdgeGlowIntensity,
                    cosmosThemeIndex,
                    cosmosPatternScale,
                    cosmosSparkleScale,
                    cosmosStarMix,
                    cosmosPulseAlpha,
                    cosmosPulseEffectOnly
            );
        } else {
            // Glass mode
            GpuTextureView blurredView = kawaseBlur.blur(
                    sceneBeforeTexture, sceneBeforeTextureView,
                    lastWidth, lastHeight,
                    blurIterations, blurRadius
            );

            if (blurredView == null) {
                capturing = false;
                return;
            }

            glassComposite.composite(
                    fb.getColorAttachmentView(),
                    sceneBeforeTextureView,
                    blurredView,
                    maskTextureView,
                    lastWidth, lastHeight,
                    saturation,
                    reflect,
                    tintColor,
                    tintIntensity,
                    edgeGlowIntensity,
                    glowColor,
                    glowIntensity
            );
        }

        capturing = false;
    }

    public void invalidate() {
        cleanupTextures();
        if (kawaseBlur != null) kawaseBlur.close();
        if (glassComposite != null) glassComposite.close();
        if (cosmosComposite != null) cosmosComposite.close();
        if (maskDiff != null) maskDiff.close();
        kawaseBlur = null;
        glassComposite = null;
        cosmosComposite = null;
        maskDiff = null;
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
        capturing = false;
    }

    public void close() {
        cleanupTextures();
        if (kawaseBlur != null) { kawaseBlur.close(); kawaseBlur = null; }
        if (glassComposite != null) { glassComposite.close(); glassComposite = null; }
        if (cosmosComposite != null) { cosmosComposite.close(); cosmosComposite = null; }
        if (maskDiff != null) { maskDiff.close(); maskDiff = null; }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
    }
}
