package ru.strange.client.renderengine.renderers;

import com.mojang.blaze3d.textures.GpuTextureView;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record GifRenderer(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        List<GpuTextureView> frames,
        List<Integer> delays,
        String gifId
) implements Renderer {
    private static final int DEFAULT_FRAME_DELAY_MS = 100;
    private static final int MIN_FRAME_DELAY_MS = 16;
    private static final Map<String, Long> startTimeCache = new ConcurrentHashMap<>();

    public static void resetAnimation(String gifId) {
        if (gifId != null && !gifId.isEmpty()) {
            startTimeCache.remove(gifId);
        }
    }

    public static void clearAnimationCache() {
        startTimeCache.clear();
    }

    public static Long getStartTimeForGif(String gifId) {
        return gifId == null || gifId.isEmpty() ? null : startTimeCache.get(gifId);
    }

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

    private int frameCount() {
        return Math.min(frames.size(), delays.size());
    }

    private boolean isRenderable() {
        int frameCount = frameCount();
        if (frameCount <= 0) {
            return false;
        }

        for (int i = 0; i < frameCount; i++) {
            if (frames.get(i) == null) {
                return false;
            }
        }
        return true;
    }

    private long getStartTime() {
        String resolvedGifId = gifId;
        if (resolvedGifId == null || resolvedGifId.isEmpty()) {
            return System.currentTimeMillis();
        }
        return startTimeCache.computeIfAbsent(resolvedGifId, key -> System.currentTimeMillis());
    }

    private int getFrameDelay(int index) {
        if (index < 0 || index >= delays.size()) {
            return DEFAULT_FRAME_DELAY_MS;
        }
        return Math.max(MIN_FRAME_DELAY_MS, delays.get(index));
    }

    private int getCurrentFrame() {
        int frameCount = frameCount();
        if (frameCount <= 1) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - getStartTime();

        long totalDelay = 0L;
        for (int i = 0; i < frameCount; i++) {
            totalDelay += getFrameDelay(i);
        }
        if (totalDelay <= 0L) {
            totalDelay = (long) frameCount * DEFAULT_FRAME_DELAY_MS;
        }

        long cycleTime = Math.floorMod(elapsed, totalDelay);
        long accumulatedDelay = 0L;
        for (int i = 0; i < frameCount; i++) {
            accumulatedDelay += getFrameDelay(i);
            if (cycleTime < accumulatedDelay) {
                return i;
            }
        }

        return frameCount - 1;
    }

    @Override
    public void render(double x, double y, DrawContext ctx) {
        if (PipelineRegistry.TEXTURE_PIPELINE == null || !isRenderable()) {
            return;
        }

        int currentFrame = getCurrentFrame();
        if (currentFrame < 0 || currentFrame >= frames.size()) {
            return;
        }

        GpuTextureView texture = frames.get(currentFrame);
        if (texture == null) {
            return;
        }

        Matrix3x2fStack guiMatrices = ctx.getMatrices();
        TextureSetup textureSetup = TextureSetup.of(texture);

        SimpleGuiRenderState state = new SimpleGuiRenderState(
                PipelineRegistry.TEXTURE_PIPELINE,
                textureSetup,
                ctx,
                createBounds(ctx, (float) x, (float) y, size.width(), size.height()),
                buffer -> {
                    DirectVertexConsumer dvc = new DirectVertexConsumer((BufferBuilder) buffer, false);

                    dvc.vertex(guiMatrices, (float) x, (float) y, 0.0f)
                            .texture(0, 0)
                            .color(this.color.color1())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, 0);

                    dvc.vertex(guiMatrices, (float) x, (float) (y + size.height()), 0.0f)
                            .texture(0, 1)
                            .color(this.color.color2())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, 0);

                    dvc.vertex(guiMatrices, (float) (x + size.width()), (float) (y + size.height()), 0.0f)
                            .texture(1, 1)
                            .color(this.color.color3())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, 0);

                    dvc.vertex(guiMatrices, (float) (x + size.width()), (float) y, 0.0f)
                            .texture(1, 0)
                            .color(this.color.color4())
                            .texture(size.width(), size.height())
                            .texture(radius.radius1(), radius.radius2())
                            .texture(radius.radius3(), radius.radius4())
                            .texture(smoothness, 0);
                }
        );

        ((DrawContextAccessor) ctx).getState().addSimpleElement(state);
    }
}
