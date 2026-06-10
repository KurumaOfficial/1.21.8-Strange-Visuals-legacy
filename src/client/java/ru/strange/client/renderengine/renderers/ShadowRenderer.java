package ru.strange.client.renderengine.renderers;

import me.x150.renderer.mixin.DrawContextAccessor;
import me.x150.renderer.render.SimpleGuiRenderState;
import me.x150.renderer.util.DirectVertexConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2fStack;
import ru.strange.client.renderengine.builders.states.QuadColorState;
import ru.strange.client.renderengine.builders.states.QuadRadiusState;
import ru.strange.client.renderengine.builders.states.SizeState;
import ru.strange.client.renderengine.renderers.pipeline.PipelineRegistry;

public record ShadowRenderer(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float offsetX,
        float offsetY,
        float blurRadius
) implements Renderer {

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
        if (PipelineRegistry.SHADOW_PIPELINE == null) return;

        double shadowX = x + offsetX;
        double shadowY = y + offsetY;

        float expandedWidth = size.width() + blurRadius * 2;
        float expandedHeight = size.height() + blurRadius * 2;
        
        double renderX = shadowX - blurRadius;
        double renderY = shadowY - blurRadius;

        Matrix3x2fStack guiMatrices = ctx.getMatrices();

        SimpleGuiRenderState state = new SimpleGuiRenderState(
                PipelineRegistry.SHADOW_PIPELINE,
                TextureSetup.empty(),
                ctx,
                createBounds(ctx, (float) renderX, (float) renderY, expandedWidth, expandedHeight),
                buffer -> {
                    DirectVertexConsumer dvc = new DirectVertexConsumer((BufferBuilder) buffer, false);

                    dvc.vertex(guiMatrices, (float) renderX, (float) renderY, (float) 0)
                            .texture(0, 0)
                            .color(color.color1())
                            .texture(expandedWidth, expandedHeight)
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, blurRadius);

                    dvc.vertex(guiMatrices, (float) renderX, (float) (renderY + expandedHeight), (float) 0)
                            .texture(0, 1)
                            .color(color.color2())
                            .texture(expandedWidth, expandedHeight)
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, blurRadius);

                    dvc.vertex(guiMatrices, (float) (renderX + expandedWidth), (float) (renderY + expandedHeight), (float) 0)
                            .texture(1, 1)
                            .color(color.color3())
                            .texture(expandedWidth, expandedHeight)
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, blurRadius);

                    dvc.vertex(guiMatrices, (float) (renderX + expandedWidth), (float) renderY, (float) 0)
                            .texture(1, 0)
                            .color(color.color4())
                            .texture(expandedWidth, expandedHeight)
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, blurRadius);
                }
        );

        ((DrawContextAccessor) ctx).getState().addSimpleElement(state);
    }
}
