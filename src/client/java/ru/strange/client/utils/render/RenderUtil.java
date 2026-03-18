package ru.strange.client.utils.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.renderengine.builders.impl.BorderBuilder;
import ru.strange.client.renderengine.builders.impl.GifBuilder;
import ru.strange.client.renderengine.builders.impl.LiquidGlassBuilder;
import ru.strange.client.renderengine.builders.impl.RectangleBuilder;
import ru.strange.client.renderengine.builders.impl.ShadowBuilder;
import ru.strange.client.renderengine.builders.impl.TextureBuilder;
import ru.strange.client.renderengine.providers.GifLoader;
import ru.strange.client.renderengine.builders.states.QuadColorState;
import ru.strange.client.renderengine.builders.states.QuadRadiusState;
import ru.strange.client.renderengine.builders.states.SizeState;
import ru.strange.client.renderengine.renderers.util.LegacyBlurUtil;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.Helper;
import ru.strange.client.utils.math.MathHelper;
import ru.strange.client.utils.math.animation.anim2.Interpolator;

import java.awt.*;
import java.util.List;

public class RenderUtil implements Helper {
    public static void drawClientRect(DrawContext ctx, float x, float y, float width, float height) {
        int color = ThemeManager.getTheme() == Theme.BLACK ? new Color(0xCC000000, true).getRGB() : ThemeManager.getTheme() == Theme.WHITE ? new Color(0xCCFFFFFF, true).getRGB() :
                    ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK ? new Color(0x99000000, true).getRGB() : ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE ? new Color(0x99FFFFFF, true).getRGB() :
                    ThemeManager.getTheme() == Theme.PURPLE ? new Color(0xB3FFCCE2, true).getRGB() : new Color(0xB3B2A4FF, true).getRGB();
        if (ThemeManager.getTheme() != Theme.BLACK && ThemeManager.getTheme() != Theme.WHITE) {
            RenderUtil.Blur.draw(ctx, x, y, width, height, 3, 10, new Color(255,255,255));
        }
        RenderUtil.Round.draw(ctx, x, y, width, height, 3, color);
    }

    public class Rect {
        public static void draw(DrawContext ctx, float x, float y, float width, float height, int color) {
            new RectangleBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, Color color) {
            new RectangleBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float smoothness, Color color) {
            new RectangleBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .smoothness(smoothness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float smoothness, int color) {
            new RectangleBuilder()
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
            new RectangleBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, Color color) {
            new RectangleBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float smoothness, Color color) {
            new RectangleBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .smoothness(smoothness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float smoothness, int color) {
            new RectangleBuilder()
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
            new BorderBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .thickness(thickness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float thickness, Color color) {
            new BorderBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .thickness(thickness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float thickness, int color) {
            new BorderBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .thickness(thickness)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float thickness, Color color) {
            new BorderBuilder()
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
        public static void draw(DrawContext ctx, Identifier identifier, float x, float y, float width, float height, int color) {
            AbstractTexture texture = mc.getTextureManager().getTexture(identifier);
            new TextureBuilder()
                    .texture(0,0, 0, 0, texture)
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, Identifier identifier, float x, float y, float width, float height, Color color) {
            AbstractTexture texture = mc.getTextureManager().getTexture(identifier);
            new TextureBuilder()
                    .texture(0,0, 0, 0, texture)
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, Identifier identifier, float x, float y, float width, float height, float radius, int color) {
            AbstractTexture texture = mc.getTextureManager().getTexture(identifier);
            new TextureBuilder()
                    .texture(0,0, 0, 0, texture)
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, Identifier identifier, float x, float y, float width, float height, float radius, Color color) {
            AbstractTexture texture = mc.getTextureManager().getTexture(identifier);
            new TextureBuilder()
                    .texture(0,0, 0, 0, texture)
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .build()
                    .render(x, y, ctx);
        }
    }

    public class Gif {
        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(QuadColorState.WHITE)
                    .frames(frames, delays)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, int color) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .frames(frames, delays)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, Color color) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .frames(frames, delays)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, float radius) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(QuadColorState.WHITE)
                    .frames(frames, delays)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, float radius, int color) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .frames(frames, delays)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, List<GpuTextureView> frames,
                                List<Integer> delays, float x, float y, float width, float height, float radius, Color color) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .frames(frames, delays)
                    .build()
                    .render(x, y, ctx);
        }

        // Удобные методы для загрузки и отрисовки GIF
        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(QuadColorState.WHITE)
                    .frames(gifData.getFrames(), gifData.getDelays())
                    .gifId(gifData.getGifId())
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, int color) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .frames(gifData.getFrames(), gifData.getDelays())
                    .gifId(gifData.getGifId())
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, Color color) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .frames(gifData.getFrames(), gifData.getDelays())
                    .gifId(gifData.getGifId())
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, float radius) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(QuadColorState.WHITE)
                    .frames(gifData.getFrames(), gifData.getDelays())
                    .gifId(gifData.getGifId())
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, float radius, int color) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .frames(gifData.getFrames(), gifData.getDelays())
                    .gifId(gifData.getGifId())
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, GifLoader.GifData gifData, float x, float y, float width, float height, float radius, Color color) {
            new GifBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .frames(gifData.getFrames(), gifData.getDelays())
                    .gifId(gifData.getGifId())
                    .build()
                    .render(x, y, ctx);
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
    }

    public class Shadow {
        public static void draw(DrawContext ctx, float x, float y, float width, float height, float blurRadius, int color) {
            new ShadowBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .blurRadius(blurRadius)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float offsetX, float offsetY, float blurRadius, int color) {
            new ShadowBuilder()
                    .size(new SizeState(width, height))
                    .radius(QuadRadiusState.NO_ROUND)
                    .color(new QuadColorState(color))
                    .offset(offsetX, offsetY)
                    .blurRadius(blurRadius)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float blurRadius, int color) {
            new ShadowBuilder()
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(new QuadColorState(color))
                    .blurRadius(blurRadius)
                    .build()
                    .render(x, y, ctx);
        }

        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            new ShadowBuilder()
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
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
        public static Color TwoColoreffect(final Color color, final Color color2, final double n) {
            final float clamp = MathHelper.clamp((float)Math.sin(18.84955592153876 * (n / 4.0 % 1.0)) / 2.0f + 0.5f, 0.0f, 1.0f);
            return new Color(MathHelper.lerp(color.getRed() / 255.0f, color2.getRed() / 255.0f, clamp), MathHelper.lerp(color.getGreen() / 255.0f, color2.getGreen() / 255.0f, clamp), MathHelper.lerp(color.getBlue() / 255.0f, color2.getBlue() / 255.0f, clamp), MathHelper.lerp(color.getAlpha() / 255.0f, color2.getAlpha() / 255.0f, clamp));
        }
        public static Color setAlpha(Color c, int alpha) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
        }

        public static int setAlpha(int color, int alpha) {
            return (color & 0x00ffffff) | (alpha << 24);
        }

        public static int getClientColor() {
            return getMainColor(10, 255);
        }

        private static Theme getTheme() {
            return GuiScreen.selectedTheme != null ? GuiScreen.selectedTheme : Theme.WHITE;
        }

        private static Theme getPreTheme() {
            return GuiScreen.preSelectedTheme != null ? GuiScreen.preSelectedTheme : Theme.WHITE;
        }

        public static int[] getClientColor(int speed, int alpha) {
            Theme theme = getTheme();
            Theme preTheme = getPreTheme();
            int[] colors = new int[4];

            colors[0] = RenderUtil.ColorUtil.applyOpacity(RenderUtil.ColorUtil.gradient(speed, 0,   RenderUtil.ColorUtil.interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(), 1)), alpha);
            colors[1] = RenderUtil.ColorUtil.applyOpacity(RenderUtil.ColorUtil.gradient(speed, 90,  RenderUtil.ColorUtil.interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(), 1)), alpha);
            colors[2] = RenderUtil.ColorUtil.applyOpacity(RenderUtil.ColorUtil.gradient(speed, 180, RenderUtil.ColorUtil.interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(), 1)), alpha);
            colors[3] = RenderUtil.ColorUtil.applyOpacity(RenderUtil.ColorUtil.gradient(speed, 270, RenderUtil.ColorUtil.interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(), 1)), alpha);

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
            int angle = 0;
            if (speed == 0) {
                angle = index % 360;
            } else {
                angle = (int) (((System.currentTimeMillis()) / speed + index) % 360);
            }
            angle = (angle >= 180 ? 360 - angle : angle) * 2;
            boolean tur = trueColor;
            return tur ? interpolateColorHue(start, end, angle / 360f) : interpolateColorC(start, end, angle / 360f);

        }

        public static Color interpolateColorHue(Color color1, Color color2, float amount) {
            amount = Math.min(1, Math.max(0, amount));

            float[] color1HSB = Color.RGBtoHSB(color1.getRed(), color1.getGreen(), color1.getBlue(), null);
            float[] color2HSB = Color.RGBtoHSB(color2.getRed(), color2.getGreen(), color2.getBlue(), null);

            Color resultColor = Color.getHSBColor(MathHelper.lerp(color1HSB[0], color2HSB[0], amount),
                    MathHelper.lerp(color1HSB[1], color2HSB[1], amount), MathHelper.lerp(color1HSB[2], color2HSB[2], amount));

            return new Color(resultColor.getRed(), resultColor.getGreen(), resultColor.getBlue(),
                    (int) MathHelper.lerp(color1.getAlpha(), color2.getAlpha(), amount));
        }

        public static Color interpolateColorC(Color color1, Color color2, float amount) {
            //amount = Math.min(1, Math.max(0, amount));
            float zalupa = amount;
            return new Color(MathHelper.lerp(color1.getRed(), color2.getRed(), zalupa),
                    MathHelper.lerp(color1.getGreen(), color2.getGreen(), zalupa),
                    MathHelper.lerp(color1.getBlue(), color2.getBlue(), zalupa),
                    MathHelper.lerp(color1.getAlpha(), color2.getAlpha(), zalupa));
        }

        public static int gradient2(int color1, int color2, int speed, int index) {
            Color col1 = new Color(color1);
            Color col2 = new Color(color2);

            double angle = (System.currentTimeMillis() / speed + index) % 360;
            float ratio = (float) ((angle %= 360) / 360.0);

            int red = (int) (col1.getRed() * (1 - ratio) + col2.getRed() * ratio);
            int green = (int) (col1.getGreen() * (1 - ratio) + col2.getGreen() * ratio);
            int blue = (int) (col1.getBlue() * (1 - ratio) + col2.getBlue() * ratio);

            Color interpolatedColor = new Color(red, green, blue);

            return interpolatedColor.getRGB();
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
            if (speed == 0) speed = 1;
            color1[0] = rainbow(speed, 1, 1, 1, 1);
            color1[1] = rainbow(speed, 90, 1, 1, 1);
            color1[2] = rainbow(speed, 180, 1, 1, 1);
            color1[3] = rainbow(speed, 270, 1, 1, 1);
            return color1;
        }

        public static int rainbow(int speed, int index, float saturation, float brightness, float opacity) {
            int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
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
            int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
            angle = angle >= 180 ? 360 - angle : angle;
            return overCol(first, second, angle / 180f);
        }

        public static int fade(int index) {
            return fade(10, index,
                    fade(),
                    multDark(fade(), 0.5F));
        }

        public static int multAlpha(int color, float percent01) {
            return getColor(red(color), green(color), blue(color), Math.round(alpha(color) * percent01));
        }

        public static int fade() {
            return RenderUtil.ColorUtil.getClientColor();
        }

        public static int gradient(int speed, int index, int... colors) {
            int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
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
            float[] rgba1 = getRGBAf(color1);
            float[] rgba2 = getRGBAf(color2);
            double r = rgba1[0] + (rgba2[0] - rgba1[0]) * offset;
            double g = rgba1[1] + (rgba2[1] - rgba1[1]) * offset;
            double b = rgba1[2] + (rgba2[2] - rgba1[2]) * offset;
            double a = rgba1[3] + (rgba2[3] - rgba1[3]) * offset;
            return rgba((int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f), (int) (a * 255.0f));
        }
        
        public static int interpolateColor(int color1, int color2, float offset) {
            return interpolateColor(color1, color2, (double)offset);
        }

        public static float[] getRGBAf(int c) {
            return new float[]{(float) red(c) / 255.F, (float) green(c) / 255.F, (float) blue(c) / 255.F, (float) alpha(c) / 255.F};
        }

        public static int skyRainbow(int speed, int index) {
            double angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
            return Color.getHSBColor(
                    ((angle %= 360) / 360.0) < 0.5 ? -((float) (angle / 360.0)) : (float) (angle / 360.0),
                    0.5F,
                    1.0F
            ).hashCode();
        }

        public static int[] getAstolfoColor(int speed) {
            int[] color1 = new int[4];
            if (speed == 0) speed = 1;
            color1[0] = skyRainbow(25, 1);
            color1[1] = skyRainbow(25, 90);
            color1[2] = skyRainbow(25, 180);
            color1[3] = skyRainbow(25, 270);
            return color1;
        }

        public static int applyOpacity(int n, float f) {
            return ColorUtil.rgba2(ColorUtil.getRedInt(n), ColorUtil.getGreenInt(n), ColorUtil.getBlueInt(n), (int)((float) ColorUtil.getAlphaInt(n) * f / 255.0f));
        }
        public static int rgba2(int n, int n2, int n3, int n4) {
            return n4 << 24 | n << 16 | n2 << 8 | n3;
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

//        public static int getClientColorOne(int speed, int index) {
//            return RenderUtil.ColorUtil.gradient(RenderUtil.ColorUtil.interpolate(GuiScreen.selectedTheme.getMain().getRGB(), GuiScreen.preSelectedTheme.getMain().getRGB(), 1 - GuiScreen.animation14.getOutput()), RenderUtil.ColorUtil.interpolate(GuiScreen.selectedTheme.getMain().getRGB(), GuiScreen.preSelectedTheme.getMain().getRGB(), 1 - GuiScreen.animation14.getOutput()), speed, index);
//        }
        public static int swapAlpha(int color, float alpha) {
            int f = color >> 16 & 0xFF;
            int f1 = color >> 8 & 0xFF;
            int f2 = color & 0xFF;
            return getColor(f, f1, f2, (int) alpha);
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
            return getColor((float) red(c) * brpc, (float) green(c) * brpc, (float) blue(c) * brpc, (float) alpha(c));
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
            return new Color((int) r, (int) g, (int) b, (int) a).getRGB();
        }

        public static int getColor(int red, int green, int blue) {
            return getColor(red, green, blue, 255);
        }

        public static int getColor(int red, int green, int blue, int alpha) {
            int color = 0;
            color |= alpha << 24;
            color |= red << 16;
            color |= green << 8;
            return color | blue;
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
            return a << 24 | r << 16 | g << 8 | b;
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

    /**
     * Liquid Glass - эффект стекла с размытием фона, френелем и искажением на краях.
     */
    public static class LiquidGlass {
        
        /**
         * Рисует Liquid Glass эффект с настройками по умолчанию.
         */
        public static void draw(DrawContext ctx, float x, float y, float width, float height) {
            new LiquidGlassBuilder()
                    .size(width, height)
                    .radius(0)
                    .color(QuadColorState.WHITE)
                    .build()
                    .render(x, y, ctx);
        }

        /**
         * Рисует Liquid Glass эффект с закруглением.
         */
        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius) {
            new LiquidGlassBuilder()
                    .size(width, height)
                    .radius(radius)
                    .color(QuadColorState.WHITE)
                    .build()
                    .render(x, y, ctx);
        }

        /**
         * Рисует Liquid Glass эффект с закруглением и цветом.
         */
        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, int color) {
            new LiquidGlassBuilder()
                    .size(width, height)
                    .radius(radius)
                    .color(color)
                    .build()
                    .render(x, y, ctx);
        }

        /**
         * Рисует Liquid Glass эффект с закруглением и цветом.
         */
        public static void draw(DrawContext ctx, float x, float y, float width, float height, float radius, Color color) {
            new LiquidGlassBuilder()
                    .size(width, height)
                    .radius(radius)
                    .color(color)
                    .build()
                    .render(x, y, ctx);
        }

        /**
         * Рисует Liquid Glass эффект с полной настройкой.
         * @param blurRadius радиус размытия фона (по умолчанию 10)
         * @param fresnelPower сила эффекта Френеля (по умолчанию 1.5)
         * @param distortStrength сила искажения на краях (по умолчанию 0.02)
         */
        public static void draw(DrawContext ctx, float x, float y, float width, float height, 
                                float radius, int color, float blurRadius, float fresnelPower, float distortStrength) {
            new LiquidGlassBuilder()
                    .size(width, height)
                    .radius(radius)
                    .color(color)
                    .blurRadius(blurRadius)
                    .fresnelPower(fresnelPower)
                    .distortStrength(distortStrength)
                    .build()
                    .render(x, y, ctx);
        }

        /**
         * Рисует Liquid Glass эффект с полной настройкой.
         */
        public static void draw(DrawContext ctx, float x, float y, float width, float height, 
                                float radius, Color color, float blurRadius, float fresnelPower, float distortStrength) {
            new LiquidGlassBuilder()
                    .size(width, height)
                    .radius(radius)
                    .color(color)
                    .blurRadius(blurRadius)
                    .fresnelPower(fresnelPower)
                    .distortStrength(distortStrength)
                    .build()
                    .render(x, y, ctx);
        }

        /**
         * Рисует Liquid Glass эффект с разными радиусами для каждого угла.
         */
        public static void draw(DrawContext ctx, float x, float y, float width, float height,
                                float r1, float r2, float r3, float r4, int color,
                                float blurRadius, float fresnelPower, float distortStrength) {
            new LiquidGlassBuilder()
                    .size(width, height)
                    .radius(r1, r2, r3, r4)
                    .color(color)
                    .blurRadius(blurRadius)
                    .fresnelPower(fresnelPower)
                    .distortStrength(distortStrength)
                    .build()
                    .render(x, y, ctx);
        }

        /**
         * Рисует Liquid Glass эффект с настройкой размытия.
         */
        public static void drawWithBlur(DrawContext ctx, float x, float y, float width, float height, 
                                        float radius, float blurRadius) {
            new LiquidGlassBuilder()
                    .size(width, height)
                    .radius(radius)
                    .color(QuadColorState.WHITE)
                    .blurRadius(blurRadius)
                    .build()
                    .render(x, y, ctx);
        }

        /**
         * Рисует Liquid Glass эффект с настройкой размытия и цветом.
         */
        public static void drawWithBlur(DrawContext ctx, float x, float y, float width, float height,
                                        float radius, int color, float blurRadius) {
            new LiquidGlassBuilder()
                    .size(width, height)
                    .radius(radius)
                    .color(color)
                    .blurRadius(blurRadius)
                    .build()
                    .render(x, y, ctx);
        }
    }
}
