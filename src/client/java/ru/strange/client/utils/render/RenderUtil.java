package ru.strange.client.utils.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.impl.interfaces.ClickGui;
import ru.strange.client.module.impl.other.Optimization;
import ru.strange.client.renderengine.builders.Builder;
import ru.strange.client.renderengine.providers.GifLoader;
import ru.strange.client.renderengine.builders.states.QuadColorState;
import ru.strange.client.renderengine.builders.states.QuadRadiusState;
import ru.strange.client.renderengine.builders.states.SizeState;
import ru.strange.client.renderengine.renderers.util.LegacyBlurUtil;
import ru.strange.client.utils.Helper;
import ru.strange.client.utils.math.MathHelper;
import ru.strange.client.utils.math.animation.anim2.Interpolator;

import java.awt.*;
import java.util.List;

public class RenderUtil implements Helper {
    private static final int CLIENT_RECT_BLACK = 0xCC000000;
    private static final int CLIENT_RECT_WHITE = 0xCCFFFFFF;
    private static final int CLIENT_RECT_TRANSPARENT_BLACK = 0x99000000;
    private static final int CLIENT_RECT_TRANSPARENT_WHITE = 0x99FFFFFF;
    private static final int CLIENT_RECT_PINK = 0xB3FFCCE2;
    private static final int CLIENT_RECT_PURPLE = 0xB3B2A4FF;
    private static final int CLIENT_RECT_BLUR_COLOR = 0xFFFFFFFF;

    public static void drawClientRect(DrawContext ctx, float x, float y, float width, float height) {
        Theme theme = ThemeManager.getTheme();
        int color = switch (theme) {
            case BLACK -> CLIENT_RECT_BLACK;
            case WHITE -> CLIENT_RECT_WHITE;
            case TRANSPARENT_BLACK -> CLIENT_RECT_TRANSPARENT_BLACK;
            case TRANSPARENT_WHITE -> CLIENT_RECT_TRANSPARENT_WHITE;
            case PINK -> CLIENT_RECT_PINK;
            case PURPLE -> CLIENT_RECT_PURPLE;
            default -> CLIENT_RECT_PURPLE;
        };

        ClickGui clickGui = ClickGui.getInstance();
        if (clickGui != null && clickGui.isGlassEnabled()) {
            int tint = 0xFFEAF2FA;
            RenderUtil.LiquidGlass.draw(ctx, x, y, width, height, 3f, tint,
                    clickGui.getGlassBlur(), clickGui.getGlassAlpha());
        } else if (theme != Theme.BLACK && theme != Theme.WHITE && !Optimization.shouldSkipHudBlur()) {
            RenderUtil.Blur.draw(ctx, x, y, width, height, 3, 10, CLIENT_RECT_BLUR_COLOR);
            RenderUtil.Round.draw(ctx, x, y, width, height, 3, color);
        } else {
            RenderUtil.Round.draw(ctx, x, y, width, height, 3, color);
        }
    }

    public class Rect {
        public static void draw(DrawContext ctx, float x, float y, float width, float height, int color) {
            Builder.rectangle()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, Color color) {
            Builder.rectangle()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float smoothness, Color color) {
            Builder.rectangle()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .smoothness(smoothness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float smoothness, int color) {
            Builder.rectangle()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .smoothness(smoothness)
                    .build()
                    .render(x, y, ctx);
        }
    }

    public class Round {
        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, int color) {
            Builder.rectangle()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, Color color) {
            Builder.rectangle()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float smoothness, Color color) {
            Builder.rectangle()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .smoothness(smoothness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float smoothness, int color) {
            Builder.rectangle()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .smoothness(smoothness)
                    .build()
                    .render(x, y, ctx);
        }
    }

    public class Border {
        public static void draw(DrawContext ctx, float x, float y, float width, float height, float thickness, int color) {
            Builder.border()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .thickness(thickness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float thickness, Color color) {
            Builder.border()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .thickness(thickness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float thickness, int color) {
            Builder.border()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .thickness(thickness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float thickness, Color color) {
            Builder.border()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .thickness(thickness)
                    .build()
                    .render(x, y, ctx);
        }
    }

    public class Blur {
        public static void draw(DrawContext ctx, float x, float y, float width, float height, float blur, int color) {
            LegacyBlurUtil.render(
                    ctx,
                    x, y,
                    new SizeState(width, height),
                    QuadRadiusState.NO_ROUND,
                    new QuadColorState(color),
                    1, blur
            );
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float blur, Color color) {
            LegacyBlurUtil.render(
                    ctx,
                    x, y,
                    new SizeState(width, height),
                    QuadRadiusState.NO_ROUND,
                    new QuadColorState(color),
                    1, blur
            );
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float blur, int color) {
            LegacyBlurUtil.render(
                    ctx,
                    x, y,
                    new SizeState(width, height),
                    new QuadRadiusState(radius),
                    new QuadColorState(color),
                    1, blur
            );
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float blur, Color color) {
            LegacyBlurUtil.render(
                    ctx,
                    x, y,
                    new SizeState(width, height),
                    new QuadRadiusState(radius),
                    new QuadColorState(color),
                    1, blur
            );
        }
    }

    public class Image {
        private static void drawTexture(DrawContext ctx, Identifier identifier, float x, float y, float width, float height,
                                        QuadRadiusState radius, QuadColorState color) {
            AbstractTexture texture = mc.getTextureManager().getTexture(identifier);
            Builder.texture()
                    .texture(0, 0, 0, 0, texture)
                    .size(new SizeState(width, height))
                    .radius(radius)
                    .color(color)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, Identifier identifier, float x, float y, float width, float height, int color) {
            drawTexture(ctx, identifier, x, y, width, height, QuadRadiusState.NO_ROUND, new QuadColorState(color));
        }

        public static void draw(DrawContext ctx, Identifier identifier, float x, float y, float width, float height, Color color) {
            drawTexture(ctx, identifier, x, y, width, height, QuadRadiusState.NO_ROUND, new QuadColorState(color));
        }

        public static void draw(DrawContext ctx, Identifier identifier, float x, float y, float width, float height, float radius, int color) {
            drawTexture(ctx, identifier, x, y, width, height, new QuadRadiusState(radius), new QuadColorState(color));
        }

        public static void draw(DrawContext ctx, Identifier identifier, float x, float y, float width, float height, float radius, Color color) {
            drawTexture(ctx, identifier, x, y, width, height, new QuadRadiusState(radius), new QuadColorState(color));
        }
    }

    public class Gif {
        private static void drawGifFrames(DrawContext ctx, List<GpuTextureView> frames, List<Integer> delays,
                                          float x, float y, float width, float height,
                                          QuadRadiusState radius, QuadColorState color, String gifId) {
            var builder = Builder.gif()
                    .size(new SizeState(width, height))
                    .radius(radius)
                    .color(color)
                    .frames(frames, delays);
            if (gifId != null) {
                builder.gifId(gifId);
            }
            builder.build().render(x, y, ctx);
        }

        private static void drawGifData(DrawContext ctx, GifLoader.GifData gifData,
                                        float x, float y, float width, float height,
                                        QuadRadiusState radius, QuadColorState color) {
            drawGifFrames(ctx, gifData.getFrames(), gifData.getDelays(), x, y, width, height, radius, color, gifData.getGifId());
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height) {
            if (!isRenderableFrames(frames, delays)) {
                return;
            }
            drawGifFrames(ctx, frames, delays, x, y, width, height, QuadRadiusState.NO_ROUND, QuadColorState.WHITE, null);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, int color) {
            if (!isRenderableFrames(frames, delays)) {
                return;
            }
            drawGifFrames(ctx, frames, delays, x, y, width, height, QuadRadiusState.NO_ROUND, new QuadColorState(color), null);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, Color color) {
            if (!isRenderableFrames(frames, delays)) {
                return;
            }
            drawGifFrames(ctx, frames, delays, x, y, width, height, QuadRadiusState.NO_ROUND, new QuadColorState(color), null);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, float radius) {
            if (!isRenderableFrames(frames, delays)) {
                return;
            }
            drawGifFrames(ctx, frames, delays, x, y, width, height, new QuadRadiusState(radius), QuadColorState.WHITE, null);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, float radius, int color) {
            if (!isRenderableFrames(frames, delays)) {
                return;
            }
            drawGifFrames(ctx, frames, delays, x, y, width, height, new QuadRadiusState(radius), new QuadColorState(color), null);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, float radius, Color color) {
            if (!isRenderableFrames(frames, delays)) {
                return;
            }
            drawGifFrames(ctx, frames, delays, x, y, width, height, new QuadRadiusState(radius), new QuadColorState(color), null);
        }

        // Удобные методы для загрузки и отрисовки GIF
        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height) {
            if (!isRenderableGif(gifData)) {
                return;
            }
            drawGifData(ctx, gifData, x, y, width, height, QuadRadiusState.NO_ROUND, QuadColorState.WHITE);
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, int color) {
            if (!isRenderableGif(gifData)) {
                return;
            }
            drawGifData(ctx, gifData, x, y, width, height, QuadRadiusState.NO_ROUND, new QuadColorState(color));
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, Color color) {
            if (!isRenderableGif(gifData)) {
                return;
            }
            drawGifData(ctx, gifData, x, y, width, height, QuadRadiusState.NO_ROUND, new QuadColorState(color));
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, float radius) {
            if (!isRenderableGif(gifData)) {
                return;
            }
            drawGifData(ctx, gifData, x, y, width, height, new QuadRadiusState(radius), QuadColorState.WHITE);
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, float radius, int color) {
            if (!isRenderableGif(gifData)) {
                return;
            }
            drawGifData(ctx, gifData, x, y, width, height, new QuadRadiusState(radius), new QuadColorState(color));
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, float radius, Color color) {
            if (!isRenderableGif(gifData)) {
                return;
            }
            drawGifData(ctx, gifData, x, y, width, height, new QuadRadiusState(radius), new QuadColorState(color));
        }

        // Методы для загрузки из файла/ресурса
        public static void draw(DrawContext ctx, java.io.File file, float x, float y, float width, float height) throws java.io.IOException {
            GifLoader.GifData gifData = GifLoader.loadGif(file);
            draw(ctx, gifData, x, y, width, height);
        }

        public static void draw(DrawContext ctx, Identifier identifier, float x, float y, float width, float height) throws java.io.IOException {
            GifLoader.GifData gifData = GifLoader.loadGif(identifier);
            draw(ctx, gifData, x, y, width, height);
        }

        private static boolean isRenderableGif(GifLoader.GifData gifData) {
            return gifData != null
                    && !gifData.isClosed()
                    && isRenderableFrames(gifData.getFrames(), gifData.getDelays());
        }

        private static boolean isRenderableFrames(List<GpuTextureView> frames, List<Integer> delays) {
            if (frames == null || delays == null || frames.isEmpty() || delays.isEmpty()) {
                return false;
            }

            int frameCount = Math.min(frames.size(), delays.size());
            if (frameCount <= 0) {
                return false;
            }

            for (int i = 0; i < frameCount; i++) {
                if (frames.get(i) == null || delays.get(i) == null) {
                    return false;
                }
            }

            return true;
        }
    }

    public class Shadow {
        public static void draw(DrawContext ctx, float x, float y, float width, float height, float blurRadius, int color) {
            Builder.shadow()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .blurRadius(blurRadius)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float offsetX, float offsetY, float blurRadius, int color) {
            Builder.shadow()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .offset(offsetX, offsetY)
                    .blurRadius(blurRadius)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float blurRadius, int color) {
            Builder.shadow()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .blurRadius(blurRadius)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            Builder.shadow()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .blurRadius(blurRadius)
                    .build()
                    .render(x, y, ctx);
        }
        
    }

    public static void setupOrientationMatrix(MatrixStack matrix, float x, float y, float z) {
        setupOrientationMatrix(matrix, (double) x, y, z);
    }

    public static void setupOrientationMatrix(MatrixStack matrix, double x, double y, double z) {
        final Vec3d renderPos = mc.getEntityRenderDispatcher().camera.getPos();
        matrix.translate(x - renderPos.x, y - renderPos.y, z - renderPos.z);
    }

    public static class ColorUtil {
        private static int clampChannel(int value) {
            return Math.max(0, Math.min(255, value));
        }

        private static float clampUnit(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }

        public static float getRed(int color) {
            return (float) (color >> 16 & 255) / 255.0F;
        }

        public static float getGreen(int color) {
            return (float) (color >> 8 & 255) / 255.0F;
        }

        public static float getBlue(int color) {
            return (float) (color & 255) / 255.0F;
        }

        public static float getAlpha(int color) {
            return (float) (color >> 24 & 255) / 255.0F;
        }

        public static Color injectAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampChannel(alpha));
        }
        public static Color TwoColoreffect(final Color color, final Color color2, final double n) {
            final float clamp = MathHelper.clamp((float)Math.sin(18.84955592153876 * (n / 4.0 % 1.0)) / 2.0f + 0.5f, 0.0f, 1.0f);
            return new Color(MathHelper.lerp(color.getRed() / 255.0f, color2.getRed() / 255.0f, clamp), MathHelper.lerp(color.getGreen() / 255.0f, color2.getGreen() / 255.0f, clamp), MathHelper.lerp(color.getBlue() / 255.0f, color2.getBlue() / 255.0f, clamp), MathHelper.lerp(color.getAlpha() / 255.0f, color2.getAlpha() / 255.0f, clamp));
        }
        public static Color setAlpha(Color c, int alpha) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), clampChannel(alpha));
        }

        public static int setAlpha(int color, int alpha) {
            return getColor(red(color), green(color), blue(color), alpha);
        }

        public static int getClientColor() {
            return getMainColor(10, 255);
        }

        public static int[] getClientColor(int speed, int alpha) {
            Theme theme = ThemeManager.getTheme();
            Theme preTheme = ThemeManager.getPreTheme();
            float progress = ThemeManager.getProgress();
            int color = RenderUtil.ColorUtil.interpolate(
                    preTheme.getMain().getRGB(),
                    theme.getMain().getRGB(),
                    progress
            );
            int[] colors = new int[4];

            colors[0] = RenderUtil.ColorUtil.applyOpacity(RenderUtil.ColorUtil.gradient(speed, 0, color), alpha);
            colors[1] = RenderUtil.ColorUtil.applyOpacity(RenderUtil.ColorUtil.gradient(speed, 90, color), alpha);
            colors[2] = RenderUtil.ColorUtil.applyOpacity(RenderUtil.ColorUtil.gradient(speed, 180, color), alpha);
            colors[3] = RenderUtil.ColorUtil.applyOpacity(RenderUtil.ColorUtil.gradient(speed, 270, color), alpha);

            return colors;
        }

        public static int getBackGroundColor(int speed, int index) {
            Theme theme = ThemeManager.getTheme();
            Theme preTheme = ThemeManager.getPreTheme();
            float t = ThemeManager.getProgress();

            int color = ColorUtil.interpolate(
                    preTheme.getBg().getRGB(),
                    theme.getBg().getRGB(),
                    t
            );

            return gradient2(color, color, speed, index);
        }

        public static int getMainColor(int speed, int index) {
            Theme theme = ThemeManager.getTheme();
            Theme preTheme = ThemeManager.getPreTheme();
            float t = ThemeManager.getProgress();

            int color = ColorUtil.interpolate(
                    preTheme.getMain().getRGB(),
                    theme.getMain().getRGB(),
                    t
            );

            return gradient2(color, color, speed, index);
        }

        public static int getTextColor(int speed, int index) {
            Theme theme = ThemeManager.getTheme();
            Theme preTheme = ThemeManager.getPreTheme();
            float t = ThemeManager.getProgress();

            int color = ColorUtil.interpolate(
                    preTheme.getText().getRGB(),
                    theme.getText().getRGB(),
                    t
            );

            return gradient2(color, color, speed, index);
        }

        public Color interpolate(Color color1, Color color2, double amount) {
            amount = 1F - amount;
            amount = (float) MathHelper.clamp(amount, 0, 1);
            return new Color(
                    Interpolator.lerp(color1.getRed(), color2.getRed(), amount),
                    Interpolator.lerp(color1.getGreen(), color2.getGreen(), amount),
                    Interpolator.lerp(color1.getBlue(), color2.getBlue(), amount),
                    Interpolator.lerp(color1.getAlpha(), color2.getAlpha(), amount)
            );
        }

        public static Color interpolateTwoColors(int speed, int index, Color start, Color end, boolean trueColor) {
            int angle = resolveAnimationAngle(speed, index);
            angle = (angle >= 180 ? 360 - angle : angle) * 2;
            boolean tur = trueColor;
            return tur ? interpolateColorHue(start, end, angle / 360f) : interpolateColorC(start, end, angle / 360f);

        }

        public static Color interpolateColorHue(Color color1, Color color2, float amount) {
            amount = clampUnit(amount);

            float[] color1HSB = Color.RGBtoHSB(color1.getRed(), color1.getGreen(), color1.getBlue(), null);
            float[] color2HSB = Color.RGBtoHSB(color2.getRed(), color2.getGreen(), color2.getBlue(), null);

            Color resultColor = Color.getHSBColor(MathHelper.lerp(color1HSB[0], color2HSB[0], amount),
                    MathHelper.lerp(color1HSB[1], color2HSB[1], amount), MathHelper.lerp(color1HSB[2], color2HSB[2], amount));

            return new Color(resultColor.getRed(), resultColor.getGreen(), resultColor.getBlue(),
                    lerpChannel(color1.getAlpha(), color2.getAlpha(), amount));
        }

        public static Color interpolateColorC(Color color1, Color color2, float amount) {
            float clampedAmount = clampUnit(amount);
            return new Color(
                    lerpChannel(color1.getRed(), color2.getRed(), clampedAmount),
                    lerpChannel(color1.getGreen(), color2.getGreen(), clampedAmount),
                    lerpChannel(color1.getBlue(), color2.getBlue(), clampedAmount),
                    lerpChannel(color1.getAlpha(), color2.getAlpha(), clampedAmount)
            );
        }

        public static int gradient2(int color1, int color2, int speed, int index) {
            float ratio = resolveAnimationAngle(speed, index) / 360.0f;
            return getColor(
                    Math.round(red(color1) * (1 - ratio) + red(color2) * ratio),
                    Math.round(green(color1) * (1 - ratio) + green(color2) * ratio),
                    Math.round(blue(color1) * (1 - ratio) + blue(color2) * ratio),
                    Math.round(alpha(color1) * (1 - ratio) + alpha(color2) * ratio)
            );
        }

        public static int interpolate(int color1, int color2, double amount) {
            amount = (float) MathHelper.clamp(amount, 0, 1);
            return getColor(
                    Interpolator.lerp(red(color1), red(color2), amount),
                    Interpolator.lerp(green(color1), green(color2), amount),
                    Interpolator.lerp(blue(color1), blue(color2), amount),
                    Interpolator.lerp(alpha(color1), alpha(color2), amount)
            );
        }

        public static int[] getRainbowColor(int speed) {
            int[] color1 = new int[4];
            color1[0] = rainbow(speed, 1, 1, 1, 1);
            color1[1] = rainbow(speed, 90, 1, 1, 1);
            color1[2] = rainbow(speed, 180, 1, 1, 1);
            color1[3] = rainbow(speed, 270, 1, 1, 1);
            return color1;
        }

        public static int rainbow(int speed, int index, float saturation, float brightness, float opacity) {
            int angle = resolveAnimationAngle(speed, index);
            float hue = angle / 360f;
            int color = Color.HSBtoRGB(hue, saturation, brightness);
            return getColor(
                    red(color),
                    green(color),
                    blue(color),
                    Math.max(0, Math.min(255, (int) (opacity * 255)))
            );
        }

        public static int overCol(int color1, int color2, float percent01) {
            final float percent = net.minecraft.util.math.MathHelper.clamp(percent01, 0F, 1F);
            return getColor(
                    Interpolator.lerp(red(color1), red(color2), percent),
                    Interpolator.lerp(green(color1), green(color2), percent),
                    Interpolator.lerp(blue(color1), blue(color2), percent),
                    Interpolator.lerp(alpha(color1), alpha(color2), percent)
            );
        }

        public int overCol(int color1, int color2) {
            return overCol(color1, color2, 0.5f);
        }

        public static int fade(int speed, int index, int first, int second) {
            int angle = resolveAnimationAngle(speed, index);
            angle = angle >= 180 ? 360 - angle : angle;
            return overCol(first, second, angle / 180f);
        }

        public static int fade(int index) {
            return fade(10, index,
                    fade(),
                    multDark(fade(), 0.5F));
        }

        public static int multAlpha(int color, float percent01) {
            return getColor(red(color), green(color), blue(color), Math.round(alpha(color) * clampUnit(percent01)));
        }

        public static int fade() {
            return RenderUtil.ColorUtil.getClientColor();
        }

        public static int gradient(int speed, int index, int... colors) {
            if (colors == null || colors.length == 0) {
                return 0;
            }
            if (colors.length == 1) {
                return colors[0];
            }

            int angle = resolveAnimationAngle(speed, index);
            angle = (angle > 180 ? 360 - angle : angle) + 180;
            int colorIndex = (int) (angle / 360f * colors.length);
            if (colorIndex == colors.length) {
                colorIndex--;
            }
            int color1 = colors[colorIndex];
            int color2 = colors[colorIndex == colors.length - 1 ? 0 : colorIndex + 1];
            return interpolateColor(color1, color2, angle / 360f * colors.length - colorIndex);
        }

        public static int interpolateColor(int color1, int color2, double offset) {
            double clampedOffset = Math.max(0.0, Math.min(1.0, offset));
            float[] rgba1 = getRGBAf(color1);
            float[] rgba2 = getRGBAf(color2);
            double r = rgba1[0] + (rgba2[0] - rgba1[0]) * clampedOffset;
            double g = rgba1[1] + (rgba2[1] - rgba1[1]) * clampedOffset;
            double b = rgba1[2] + (rgba2[2] - rgba1[2]) * clampedOffset;
            double a = rgba1[3] + (rgba2[3] - rgba1[3]) * clampedOffset;
            return rgba((int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f), (int) (a * 255.0f));
        }
        
        public static int interpolateColor(int color1, int color2, float offset) {
            return interpolateColor(color1, color2, (double)offset);
        }

        public static float[] getRGBAf(int c) {
            return new float[]{(float) red(c) / 255.F, (float) green(c) / 255.F, (float) blue(c) / 255.F, (float) alpha(c) / 255.F};
        }

        public static int skyRainbow(int speed, int index) {
            double angle = resolveAnimationAngle(speed, index);
            return Color.getHSBColor(
                    ((angle %= 360) / 360.0) < 0.5 ? -((float) (angle / 360.0)) : (float) (angle / 360.0),
                    0.5F,
                    1.0F
            ).getRGB();
        }

        public static int[] getAstolfoColor(int speed) {
            int[] color1 = new int[4];
            color1[0] = skyRainbow(speed, 1);
            color1[1] = skyRainbow(speed, 90);
            color1[2] = skyRainbow(speed, 180);
            color1[3] = skyRainbow(speed, 270);
            return color1;
        }

        private static int resolveAnimationAngle(int speed, int index) {
            if (speed <= 0) {
                return Math.floorMod(index, 360);
            }

            return (int) ((System.currentTimeMillis() / speed + index) % 360);
        }

        private static int lerpChannel(int start, int end, float amount) {
            return clampChannel(Math.round(MathHelper.lerp(start, end, amount)));
        }

        public static int applyOpacity(int n, float f) {
            int alpha = Math.round(ColorUtil.getAlphaInt(n) * clampUnit(f / 255.0f));
            return ColorUtil.getColor(ColorUtil.getRedInt(n), ColorUtil.getGreenInt(n), ColorUtil.getBlueInt(n), alpha);
        }
        public static int rgba2(int n, int n2, int n3, int n4) {
            return getColor(n, n2, n3, n4);
        }

        public static int getRedInt(int n) {
            return n >> 16 & 0xFF;
        }

        public static int getGreenInt(int n) {
            return n >> 8 & 0xFF;
        }

        public static int getBlueInt(int n) {
            return n & 0xFF;
        }

        public static int getAlphaInt(int n) {
            return n >> 24 & 0xFF;
        }

        public static float[] getColorComps(Color color) {
            return new float[]{color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f};
        }

        public static int swapAlpha(int color, float alpha) {
            int f = color >> 16 & 0xFF;
            int f1 = color >> 8 & 0xFF;
            int f2 = color & 0xFF;
            return getColor(f, f1, f2, Math.round(alpha));
        }

        public static Color getColor(int color) {
            int r = color >> 16 & 0xFF;
            int g = color >> 8 & 0xFF;
            int b = color & 0xFF;
            int a = color >> 24 & 0xFF;
            return new Color(r, g, b, a);
        }

        public static int replAlpha(int c, int a) {
            return getColor(red(c), green(c), blue(c), a);
        }

        public static int multDark(int c, float brpc) {
            float brightness = clampUnit(brpc);
            return getColor((float) red(c) * brightness, (float) green(c) * brightness, (float) blue(c) * brightness, (float) alpha(c));
        }

        public static int red(int c) {
            return c >> 16 & 0xFF;
        }

        public static int green(int c) {
            return c >> 8 & 0xFF;
        }

        public static int blue(int c) {
            return c & 0xFF;
        }

        public static int alpha(int c) {
            return c >> 24 & 0xFF;
        }

        public static int getColor(float r, float g, float b, float a) {
            return new Color(clampChannel((int) r), clampChannel((int) g), clampChannel((int) b), clampChannel((int) a)).getRGB();
        }

        public static int getColor(int red, int green, int blue) {
            return getColor(red, green, blue, 255);
        }

        public static int getColor(int red, int green, int blue, int alpha) {
            int resolvedRed = clampChannel(red);
            int resolvedGreen = clampChannel(green);
            int resolvedBlue = clampChannel(blue);
            int resolvedAlpha = clampChannel(alpha);
            int color = 0;
            color |= resolvedAlpha << 24;
            color |= resolvedRed << 16;
            color |= resolvedGreen << 8;
            return color | resolvedBlue;
        }

        public static int getRedFromColor(int color) {
            return color >> 16 & 0xFF;
        }

        public static int getGreenFromColor(int color) {
            return color >> 8 & 0xFF;
        }

        public static int getBlueFromColor(int color) {
            return color & 0xFF;
        }

        public static int getAlphaFromColor(int color) {
            return color >> 24 & 0xFF;
        }

        public static float[] rgb(final int color) {
            return new float[]{(color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, (color >> 24 & 0xFF) / 255f};
        }

        public static int rgba(final int r, final int g, final int b, final int a) {
            return getColor(r, g, b, a);
        }

        public static int colorToHex(Color color) {
            int a = color.getAlpha();
            int r = color.getRed();
            int g = color.getGreen();
            int b = color.getBlue();

            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        public static float[] rgba(final int color) {
            return new float[]{
                    (color >> 16 & 0xFF) / 255f,
                    (color >> 8 & 0xFF) / 255f,
                    (color & 0xFF) / 255f,
                    (color >> 24 & 0xFF) / 255f
            };
        }
    }

    public static class LiquidGlass {

        public static void draw(DrawContext ctx, float x, float y, float width, float height,
                                float radius, int color, float blurRadius, float alpha) {
            drawGlass(ctx, x, y, width, height, new QuadRadiusState(radius), blurRadius, alpha);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height,
                                float radius, Color color, float blurRadius, float alpha) {
            drawGlass(ctx, x, y, width, height, new QuadRadiusState(radius), blurRadius, alpha);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height,
                                float r1, float r2, float r3, float r4, int color,
                                float blurRadius, float alpha) {
            drawGlass(ctx, x, y, width, height, new QuadRadiusState(r1, r2, r3, r4), blurRadius, alpha);
        }

        private static void drawGlass(DrawContext ctx, float x, float y, float width, float height,
                                       QuadRadiusState radius, float blurRadius, float alpha) {
            float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
            if (clampedAlpha <= 0.01f) {
                return;
            }

            // 1. Blurred background through working pipeline
            LegacyBlurUtil.render(ctx, x, y,
                    new SizeState(width, height), radius,
                    new QuadColorState(ColorUtil.replAlpha(0xFFFFFFFF, (int) (255.0f * clampedAlpha))),
                    1, blurRadius);

            // 2. Semi-transparent glass tint overlay
            int tintAlpha = (int) (clampedAlpha * 90);   // subtle tint, max ~35%
            int glassTint = (tintAlpha << 24) | 0xDCE4EE; // cool neutral blue-gray
            Builder.rectangle()
                    .size(new SizeState(width, height))
                    .radius(radius)
                    .color(new QuadColorState(glassTint))
                    .build()
                    .render(x, y, ctx);

            // 3. Top highlight gradient (glass reflection)
            int highlightAlpha = (int) (clampedAlpha * 40);
            int highlight = (highlightAlpha << 24) | 0xFFFFFF;
            int transparent = 0x00FFFFFF;
            Builder.rectangle()
                    .size(new SizeState(width, height * 0.45f))
                    .radius(new QuadRadiusState(radius.radius1(), radius.radius2(), 0, 0))
                    .color(new QuadColorState(highlight, highlight, transparent, transparent))
                    .build()
                    .render(x, y, ctx);

            // 4. Subtle edge border
            int borderAlpha = (int) (clampedAlpha * 55);
            int borderColor = (borderAlpha << 24) | 0xFFFFFF;
            Builder.border()
                    .size(new SizeState(width, height))
                    .radius(radius)
                    .color(new QuadColorState(borderColor))
                    .thickness(0.5f)
                    .build()
                    .render(x, y, ctx);
        }
    }

}
