package ru.strange.client.renderengine.renderers.util;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import ru.strange.client.renderengine.renderers.pipeline.ScreenFilterPipeline;

public final class ScreenFilterRenderer {

    private static ScreenFilterRenderer instance;

    private final MinecraftClient client;
    private final ScreenFilterPipeline pipeline = new ScreenFilterPipeline();

    private GpuTexture sceneTexture;
    private GpuTextureView sceneTextureView;
    private int lastWidth;
    private int lastHeight;

    private float brightness = 0.0f;
    private float contrast = 1.0f;
    private float saturation = 1.0f;
    private float exposure = 1.0f;
    private float gamma = 1.0f;
    private float vignette = 0.0f;
    private float grain = 0.0f;
    private int tintColor = 0xFFFFFFFF;
    private float tintIntensity = 0.0f;
    private long startTime = System.nanoTime();

    private ScreenFilterRenderer() {
        this.client = MinecraftClient.getInstance();
    }

    public static ScreenFilterRenderer getInstance() {
        if (instance == null) {
            instance = new ScreenFilterRenderer();
        }
        return instance;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
    }

    public void setExposure(float exposure) {
        this.exposure = exposure;
    }

    public void setGamma(float gamma) {
        this.gamma = gamma;
    }

    public void setVignette(float vignette) {
        this.vignette = vignette;
    }

    public void setGrain(float grain) {
        this.grain = grain;
    }

    public void setTintColor(int tintColor) {
        this.tintColor = tintColor;
    }

    public void setTintIntensity(float tintIntensity) {
        this.tintIntensity = tintIntensity;
    }

    public void render() {
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) {
            return;
        }

        ensureSceneTexture(framebuffer.textureWidth, framebuffer.textureHeight);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                framebuffer.getColorAttachment(),
                sceneTexture,
                0, 0, 0, 0, 0,
                framebuffer.textureWidth, framebuffer.textureHeight
        );

        float time = (System.nanoTime() - startTime) / 1_000_000_000.0f;
        pipeline.composite(
                framebuffer.getColorAttachmentView(),
                sceneTextureView,
                framebuffer.textureWidth,
                framebuffer.textureHeight,
                brightness,
                contrast,
                saturation,
                exposure,
                gamma,
                vignette,
                grain,
                tintColor,
                tintIntensity,
                time
        );
    }

    private void ensureSceneTexture(int width, int height) {
        if (sceneTexture != null && width == lastWidth && height == lastHeight) {
            return;
        }

        cleanupTextures();
        sceneTexture = RenderSystem.getDevice().createTexture(
                () -> "strange:screen_filter_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        sceneTextureView = RenderSystem.getDevice().createTextureView(sceneTexture);
        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupTextures() {
        if (sceneTextureView != null) {
            sceneTextureView.close();
            sceneTextureView = null;
        }
        if (sceneTexture != null) {
            sceneTexture.close();
            sceneTexture = null;
        }
        lastWidth = 0;
        lastHeight = 0;
    }

    public void close() {
        cleanupTextures();
        pipeline.close();
    }
}
