package ru.strange.client.renderengine.renderers.util;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import ru.strange.client.Strange;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public final class LegacyBlurProcessor {
    private final KawaseBlur screenBlur = KawaseBlur.getInstance();
    private boolean loggedColorAttachmentReflectionFailure;

    private int downscaledCaptureTex = 0;
    private int downscaledCaptureW = 0;
    private int downscaledCaptureH = 0;
    private int downscaledCaptureFbo = 0;
    private float blurCaptureScaleX = 0.35f;
    private float blurCaptureScaleY = 0.35f;

    private int preparedBlurTex = 0;
    private int preparedBlurW = 0;
    private int preparedBlurH = 0;
    private float preparedBlurScaleX = 1.0f;
    private float preparedBlurScaleY = 1.0f;

    private int fullFrameReadFbo = 0;

    private int lastFrame = -1;
    private float lastRadius = -1.0f;
    private int lastScreenW = -1;
    private int lastScreenH = -1;

    public void prepareScreenBlur(int screenW, int screenH, float radiusPx) {
        if (screenW <= 0 || screenH <= 0) {
            preparedBlurTex = 0;
            preparedBlurW = 0;
            preparedBlurH = 0;
            preparedBlurScaleX = 1.0f;
            preparedBlurScaleY = 1.0f;
            return;
        }

        int currentFrame = FrameTracker.getFrame();
        boolean newFrame = currentFrame != lastFrame;

        // If same frame and same parameters, reuse existing result
        if (!newFrame && Math.abs(radiusPx - lastRadius) < 0.1f && screenW == lastScreenW && screenH == lastScreenH && preparedBlurTex != 0) {
            return;
        }

        // Optimization: Only re-capture screen if it's a new frame or resolution changed
        boolean needsCapture = newFrame || screenW != lastScreenW || screenH != lastScreenH;

        float requestedScaleX = 1.0f;
        float requestedScaleY = 1.0f;
        float userScaleFloorX = blurCaptureScaleX;
        float userScaleFloorY = blurCaptureScaleY;
        float smallKernelThreshold = screenBlur.smallKernelThreshold();
        if (radiusPx > smallKernelThreshold) {
            float minimumRadius = screenBlur.minimumRadius();
            float radius = Math.max(radiusPx, minimumRadius);
            float adaptiveScale = smallKernelThreshold / radius;
            adaptiveScale = Math.max(adaptiveScale, 0.2f);
            requestedScaleX = Math.min(requestedScaleX, adaptiveScale);
            requestedScaleY = Math.min(requestedScaleY, adaptiveScale);
        }

        requestedScaleX = Math.max(requestedScaleX, userScaleFloorX);
        requestedScaleY = Math.max(requestedScaleY, userScaleFloorY);

        ensureDownscaledCapture(screenW, screenH, requestedScaleX, requestedScaleY);
        if (downscaledCaptureTex == 0 || downscaledCaptureFbo == 0) {
            preparedBlurTex = 0;
            preparedBlurW = 0;
            preparedBlurH = 0;
            preparedBlurScaleX = 1.0f;
            preparedBlurScaleY = 1.0f;
            return;
        }

        float actualScaleX = downscaledCaptureW / (float) Math.max(1, screenW);
        float actualScaleY = downscaledCaptureH / (float) Math.max(1, screenH);

        if (needsCapture) {
            GlState.Snapshot state = GlState.push();
            try {
                boolean wasScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
                boolean wasSrgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
                if (wasScissorEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);
                if (wasSrgb) GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);

                MinecraftClient client = MinecraftClient.getInstance();
                int mainFramebufferTextureId = 0;
                if (client != null) {
                    Framebuffer mainFramebuffer = client.getFramebuffer();
                    mainFramebufferTextureId = extractColorTextureId(mainFramebuffer);
                }

                if (mainFramebufferTextureId > 0) {
                    if (fullFrameReadFbo == 0) {
                        fullFrameReadFbo = GL30.glGenFramebuffers();
                    }
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fullFrameReadFbo);
                    GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                            GL11.GL_TEXTURE_2D, mainFramebufferTextureId, 0);
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                } else {
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                    GL11.glReadBuffer(GL11.GL_BACK);
                }

                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, downscaledCaptureFbo);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);

                GL30.glBlitFramebuffer(0, 0, screenW, screenH,
                        0, 0, downscaledCaptureW, downscaledCaptureH,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

                if (wasScissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                if (wasSrgb) GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            } finally {
                GlState.pop(state);
            }
        }

        float radiusScale = (float) Math.sqrt(Math.max(0f, actualScaleX) * Math.max(0f, actualScaleY));
        float scaledRadius = Math.max(0f, radiusPx) * radiusScale;
        int blurred = screenBlur.blurFromColorTexture(downscaledCaptureTex,
                downscaledCaptureW, downscaledCaptureH, scaledRadius);

        if (blurred == 0) {
            preparedBlurTex = 0;
            preparedBlurW = 0;
            preparedBlurH = 0;
            preparedBlurScaleX = 1.0f;
            preparedBlurScaleY = 1.0f;
            return;
        }

        preparedBlurTex = blurred;
        preparedBlurW = downscaledCaptureW;
        preparedBlurH = downscaledCaptureH;
        preparedBlurScaleX = actualScaleX;
        preparedBlurScaleY = actualScaleY;

        lastFrame = currentFrame;
        lastRadius = radiusPx;
        lastScreenW = screenW;
        lastScreenH = screenH;
    }

    public int getPreparedBlurTex() {
        return preparedBlurTex;
    }

    public int getPreparedBlurW() {
        return preparedBlurW;
    }

    public int getPreparedBlurH() {
        return preparedBlurH;
    }

    public float getPreparedBlurScaleX() {
        return preparedBlurScaleX;
    }

    public float getPreparedBlurScaleY() {
        return preparedBlurScaleY;
    }

    private void ensureDownscaledCapture(int screenW, int screenH, float scaleX, float scaleY) {
        if (screenW <= 0 || screenH <= 0) {
            return;
        }
        if (!Float.isFinite(scaleX) || !Float.isFinite(scaleY)) {
            throw new IllegalArgumentException("Blur capture scale must be finite");
        }
        if (scaleX <= 0f || scaleY <= 0f) {
            throw new IllegalArgumentException("Blur capture scale must be positive");
        }

        int clampedSrcW = Math.max(1, screenW);
        int clampedSrcH = Math.max(1, screenH);
        int targetW = Math.max(1, Math.round(clampedSrcW * scaleX));
        int targetH = Math.max(1, Math.round(clampedSrcH * scaleY));

        if (downscaledCaptureTex != 0 && targetW == downscaledCaptureW && targetH == downscaledCaptureH) {
            return;
        }

        if (downscaledCaptureTex != 0) {
            GL11.glDeleteTextures(downscaledCaptureTex);
            downscaledCaptureTex = 0;
        }
        if (downscaledCaptureFbo != 0) {
            GL30.glDeleteFramebuffers(downscaledCaptureFbo);
            downscaledCaptureFbo = 0;
        }

        downscaledCaptureTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, downscaledCaptureTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, targetW, targetH, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        downscaledCaptureFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, downscaledCaptureFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, downscaledCaptureTex, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glDeleteFramebuffers(downscaledCaptureFbo);
            GL11.glDeleteTextures(downscaledCaptureTex);
            downscaledCaptureFbo = 0;
            downscaledCaptureTex = 0;
            throw new IllegalStateException("Downscaled capture FBO incomplete: status=" + status);
        }

        downscaledCaptureW = targetW;
        downscaledCaptureH = targetH;
    }

    private int extractColorTextureId(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return 0;
        }
        GpuTexture colorAttachment = framebuffer.getColorAttachment();
        if (colorAttachment instanceof GlTexture glColor) {
            return glColor.getGlId();
        }
        // Try getColorAttachmentView via reflection (compatible with different mappings)
        try {
            Method m = framebuffer.getClass().getMethod("getColorAttachmentView");
            Object viewObj = m.invoke(framebuffer);
            if (viewObj instanceof GpuTextureView view) {
                GpuTexture tex = view.texture();
                if (tex instanceof GlTexture glViewTex) {
                    return glViewTex.getGlId();
                }
            }
        } catch (ReflectiveOperationException | ClassCastException | SecurityException exception) {
            if (!loggedColorAttachmentReflectionFailure) {
                loggedColorAttachmentReflectionFailure = true;
                Strange.LOGGER.debug("LegacyBlurProcessor could not access framebuffer color attachment view reflectively", exception);
            }
        }
        return 0;
    }
}
