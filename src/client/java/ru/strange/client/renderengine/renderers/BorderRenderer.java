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

public record BorderRenderer(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float thickness,
        float internalSmooth,
        float externalSmooth
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
        if (PipelineRegistry.BORDER_PIPELINE == null) return;

        Matrix3x2fStack guiMatrices = ctx.getMatrices();

        SimpleGuiRenderState state = new SimpleGuiRenderState(
                PipelineRegistry.BORDER_PIPELINE,
                TextureSetup.empty(),
                ctx,
                createBounds(ctx, (float) x, (float) y, size.width(), size.height()),
                buffer -> {
                    DirectVertexConsumer dvc = new DirectVertexConsumer((BufferBuilder) buffer, false);

                    // Вершина 1 (top-left)
                    dvc.vertex(guiMatrices, (float) x, (float) y, (float) 0)
                            .texture(0, 0)
                            .color(this.color.color1())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(thickness, internalSmooth)
                            .texture(externalSmooth, 0);

                    // Вершина 2 (bottom-left)
                    dvc.vertex(guiMatrices, (float) x, (float) (y + size.height()), (float) 0)
                            .texture(0, 1)
                            .color(this.color.color2())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(thickness, internalSmooth)
                            .texture(externalSmooth, 0);

                    // Вершина 3 (bottom-right)
                    dvc.vertex(guiMatrices, (float) (x + size.width()), (float) (y + size.height()), (float) 0)
                            .texture(1, 1)
                            .color(this.color.color3())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(thickness, internalSmooth)
                            .texture(externalSmooth, 0);

                    // Вершина 4 (top-right)
                    dvc.vertex(guiMatrices, (float) (x + size.width()), (float) y, (float) 0)
                            .texture(1, 0)
                            .color(this.color.color4())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(thickness, internalSmooth)
                            .texture(externalSmooth, 0);
                }
        );

        ((DrawContextAccessor) ctx).getState().addSimpleElement(state);
    }
}