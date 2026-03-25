package ru.strange.client.renderengine.renderers;

import com.mojang.blaze3d.textures.GpuTexture;
import me.x150.renderer.mixin.DrawContextAccessor;
import me.x150.renderer.render.SimpleGuiRenderState;
import me.x150.renderer.util.DirectVertexConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import ru.strange.client.Strange;
import ru.strange.client.renderengine.builders.states.QuadColorState;
import ru.strange.client.renderengine.builders.states.QuadRadiusState;
import ru.strange.client.renderengine.builders.states.SizeState;
import ru.strange.client.renderengine.renderers.pipeline.PipelineRegistry;
import ru.strange.client.renderengine.renderers.util.GlState;
import ru.strange.client.renderengine.renderers.util.LegacyBlurProcessor;

public record LiquidGlassRenderer(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float blurRadius,
        float fresnelPower,
        float distortStrength
) implements Renderer {

    private static final SimpleFramebuffer BLUR_TARGET = new SimpleFramebuffer("strange_liquidglass_target", 16, 16, false);
    private static int BLUR_READ_FBO = 0;
    private static int BLUR_DRAW_FBO = 0;
    private static final LegacyBlurProcessor LEGACY_BLUR = new LegacyBlurProcessor();

    private static ScreenRect createBounds(DrawContext c, float x, float y, float w, float h) {
        Matrix3x2fStack mat = c.getMatrices();
        DrawContext.ScissorStack ss = ((DrawContextAccessor) c).getScissorStack();
        ScreenRect scissor = ss.peekLast();

        ScreenRect screenRect = new ScreenRect(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.ceil(w),
                (int) Math.ceil(h)
        ).transformEachVertex(mat);

        return scissor != null ? scissor.intersection(screenRect) : screenRect;
    }

    @Override
    public void render(double x, double y, DrawContext ctx) {
        if (PipelineRegistry.LIQUID_GLASS_PIPELINE == null) {
            Strange.LOGGER.warn("LiquidGlass pipeline is not initialized");
            return;
        }

        final var client = MinecraftClient.getInstance();

        int screenW = client.getWindow().getFramebufferWidth();
        int screenH = client.getWindow().getFramebufferHeight();
        LEGACY_BLUR.prepareScreenBlur(screenW, screenH, blurRadius);
        int blurredTexture = LEGACY_BLUR.getPreparedBlurTex();
        if (blurredTexture == 0) {
            Strange.LOGGER.warn("LiquidGlass blur preparation returned an invalid texture id");
            return;
        }

        int blurW = LEGACY_BLUR.getPreparedBlurW();
        int blurH = LEGACY_BLUR.getPreparedBlurH();
        if (blurW <= 0 || blurH <= 0) {
            Strange.LOGGER.warn("LiquidGlass blur dimensions are invalid: {}x{}", blurW, blurH);
            return;
        }

        if (BLUR_TARGET.textureWidth != blurW || BLUR_TARGET.textureHeight != blurH) {
            BLUR_TARGET.resize(blurW, blurH);
        }
        var blurView = BLUR_TARGET.getColorAttachmentView();
        if (blurView == null) {
            Strange.LOGGER.warn("LiquidGlass blur target view is null");
            return;
        }
        int blurTargetTexId = 0;
        GpuTexture blurTexture = blurView.texture();
        if (blurTexture instanceof GlTexture glBlurTexture) blurTargetTexId = glBlurTexture.getGlId();
        if (blurTargetTexId == 0) {
            Strange.LOGGER.warn("LiquidGlass blur target texture id is invalid");
            return;
        }

        GlState.Snapshot snapshot = GlState.push();
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);

            if (BLUR_READ_FBO == 0) {
                BLUR_READ_FBO = GL30.glGenFramebuffers();
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, BLUR_READ_FBO);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, blurredTexture, 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

            if (BLUR_DRAW_FBO == 0) {
                BLUR_DRAW_FBO = GL30.glGenFramebuffers();
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, BLUR_DRAW_FBO);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, blurTargetTexId, 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, blurW, blurH,
                    0, 0, blurW, blurH,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        } finally {
            GlState.pop(snapshot);
        }

        Matrix3x2fStack guiMatrices = ctx.getMatrices();

        // Вычисляем UV координаты для текстуры размытия
        float scale = (float) client.getWindow().getScaleFactor();

        float px1 = (float) x * scale;
        float py1 = (float) y * scale;
        float px2 = (float) (x + size.width()) * scale;
        float py2 = (float) (y + size.height()) * scale;

        float uScale = LEGACY_BLUR.getPreparedBlurScaleX() / blurW;
        float vScale = -LEGACY_BLUR.getPreparedBlurScaleY() / blurH;
        float uOffset = 0.0f;
        float vOffset = blurH > 0 ? 1.0f : 0.0f;

        float u1 = px1 * uScale + uOffset;
        float v1 = py1 * vScale + vOffset;
        float u2 = px2 * uScale + uOffset;
        float v2 = py2 * vScale + vOffset;
        
        float centerU = (u1 + u2) * 0.5f;
        float centerV = (v1 + v2) * 0.5f;

        SimpleGuiRenderState state = new SimpleGuiRenderState(
                PipelineRegistry.LIQUID_GLASS_PIPELINE,
                TextureSetup.of(BLUR_TARGET.getColorAttachmentView()),
                ctx,
                createBounds(ctx, (float) x, (float) y, size.width(), size.height()),
                buffer -> {
                    DirectVertexConsumer dvc = new DirectVertexConsumer((BufferBuilder) buffer, false);

                    dvc.vertex(guiMatrices, (float) x, (float) y, 0)
                            .texture(0, 0)
                            .color(this.color.color1())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, 2.0f)
                            .texture(fresnelPower, distortStrength)
                            .texture(u1, v1)
                            .texture(centerU, centerV);

                    dvc.vertex(guiMatrices, (float) x, (float) (y + size.height()), 0)
                            .texture(0, 1)
                            .color(this.color.color2())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, 2.0f)
                            .texture(fresnelPower, distortStrength)
                            .texture(u1, v2)
                            .texture(centerU, centerV);

                    dvc.vertex(guiMatrices, (float) (x + size.width()), (float) (y + size.height()), 0)
                            .texture(1, 1)
                            .color(this.color.color3())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, 2.0f)
                            .texture(fresnelPower, distortStrength)
                            .texture(u2, v2)
                            .texture(centerU, centerV);

                    dvc.vertex(guiMatrices, (float) (x + size.width()), (float) y, 0)
                            .texture(1, 0)
                            .color(this.color.color4())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, 2.0f)
                            .texture(fresnelPower, distortStrength)
                            .texture(u2, v1)
                            .texture(centerU, centerV);
                }
        );

        ((DrawContextAccessor) ctx).getState().addSimpleElement(state);
    }
}
