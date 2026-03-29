package ru.strange.client.module.impl.utilities;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.renderengine.renderers.pipeline.CosmosCompositePipeline;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;

public final class GpsHudShaderPass {
    private static final long START_TIME = System.nanoTime();

    private static PendingMarker pendingMarker;
    private static CosmosCompositePipeline cosmosComposite;

    private static GpuTexture sceneTex;
    private static GpuTextureView sceneView;
    private static int lastWidth;
    private static int lastHeight;

    private GpsHudShaderPass() {
    }

    public static void queue(Identifier maskTexture, float x, float y, float width, float height, int alpha, ShaderThemePreset preset) {
        if (maskTexture == null || preset == null) {
            pendingMarker = null;
            return;
        }

        pendingMarker = new PendingMarker(maskTexture, x, y, width, height, alpha, preset);
    }

    public static void clearQueued() {
        pendingMarker = null;
    }

    public static void renderQueued() {
        PendingMarker marker = pendingMarker;
        pendingMarker = null;
        if (marker == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) {
            return;
        }

        AbstractTexture texture = client.getTextureManager().getTexture(marker.maskTexture());
        if (texture == null || texture.getGlTextureView() == null) {
            return;
        }

        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        if (width <= 0 || height <= 0) {
            return;
        }

        int scaledWidth = Math.max(1, client.getWindow().getScaledWidth());
        int scaledHeight = Math.max(1, client.getWindow().getScaledHeight());
        float scaleX = width / (float) scaledWidth;
        float scaleY = height / (float) scaledHeight;

        ensureInitialized();
        ensureTextures(width, height);
        captureScene(framebuffer, width, height);

        float markerX = marker.x() * scaleX;
        float markerY = marker.y() * scaleY;
        float markerWidthPx = marker.width() * scaleX;
        float markerHeightPx = marker.height() * scaleY;

        ShaderThemePreset preset = marker.preset();
        float time = (float) ((System.nanoTime() - START_TIME) / 1_000_000_000.0);
        float detailBoost = resolveHudDetailBoost(width, height, markerWidthPx, markerHeightPx);
        cosmosComposite.composite(
                framebuffer.getColorAttachmentView(),
                sceneView,
                sceneView,
                texture.getGlTextureView(),
                width,
                height,
                time,
                52.0f * preset.densityScale() * detailBoost,
                0.30f * preset.speedScale(),
                1.12f * preset.intensityScale(),
                preset.primaryColor(),
                preset.accentColor(),
                0.54f * preset.edgeScale(),
                preset.themeIndex(),
                preset.patternScale() * 1.08f * detailBoost,
                preset.sparkleScale() * (1.0f + (detailBoost - 1.0f) * 0.45f),
                preset.starMix(),
                preset.isPulse() ? 0.84f : 1.0f,
                false,
                markerX,
                markerY,
                markerWidthPx,
                markerHeightPx,
                marker.alpha() / 255.0f,
                true
        );
    }

    private static float resolveHudDetailBoost(int screenWidth, int screenHeight, float markerWidth, float markerHeight) {
        float safeMarkerWidth = Math.max(1.0f, markerWidth);
        float safeMarkerHeight = Math.max(1.0f, markerHeight);
        float screenArea = Math.max(1.0f, screenWidth * (float) screenHeight);
        float markerArea = safeMarkerWidth * safeMarkerHeight;
        return MathHelper.clamp((float) Math.sqrt(screenArea / markerArea) * 0.18f, 1.8f, 7.0f);
    }

    private static void ensureInitialized() {
        if (cosmosComposite == null) {
            cosmosComposite = new CosmosCompositePipeline();
        }
    }

    private static void ensureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneTex != null) {
            return;
        }

        cleanupTextures();

        sceneTex = createTexture("strange:gps_hud_scene", TextureFormat.RGBA8, width, height);
        sceneView = RenderSystem.getDevice().createTextureView(sceneTex);
        lastWidth = width;
        lastHeight = height;
    }

    private static void captureScene(Framebuffer framebuffer, int width, int height) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(framebuffer.getColorAttachment(), sceneTex, 0, 0, 0, 0, 0, width, height);
    }

    private static GpuTexture createTexture(String name, TextureFormat format, int width, int height) {
        return RenderSystem.getDevice().createTexture(
                () -> name,
                GpuTexture.USAGE_COPY_SRC
                        | GpuTexture.USAGE_COPY_DST
                        | GpuTexture.USAGE_TEXTURE_BINDING
                        | GpuTexture.USAGE_RENDER_ATTACHMENT,
                format,
                width,
                height,
                1,
                1
        );
    }

    private static void cleanupTextures() {
        if (sceneView != null) {
            sceneView.close();
            sceneView = null;
        }
        if (sceneTex != null) {
            sceneTex.close();
            sceneTex = null;
        }
        lastWidth = 0;
        lastHeight = 0;
    }

    private record PendingMarker(Identifier maskTexture, float x, float y, float width, float height, int alpha,
                                 ShaderThemePreset preset) {
    }
}
