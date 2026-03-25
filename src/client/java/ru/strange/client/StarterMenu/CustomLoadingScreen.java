package ru.strange.client.StarterMenu;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.math.MathHelper;

public final class CustomLoadingScreen {
    private static final int LOGO_TEXTURE_SIZE = 120;
    private static final int LOGO_HALF_HEIGHT = 60;

    private CustomLoadingScreen() {
    }

    public static void render(DrawContext context, int width, int height, float progress, long timeMs, float alpha, boolean reloading) {
        if (width <= 0 || height <= 0) {
            return;
        }

        float clampedAlpha = MathHelper.clamp(alpha, 0.0F, 1.0F);
        int bgColor = withAlpha(0x000000, clampedAlpha);
        context.fill(0, 0, width, height, bgColor);

        int centerX = width / 2;
        int centerY = height / 2;
        int logoScale = (int) (Math.min(width * 0.75D, height) * 0.25D);
        int logoHalfWidth = (int) (logoScale * 2.0D);
        int logoHalfHeight = logoScale / 2;
        int logoTint = withAlpha(0xFFFFFF, clampedAlpha);

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                SplashOverlay.LOGO,
                centerX - logoHalfWidth,
                centerY - logoHalfHeight,
                -0.0625F,
                0.0F,
                logoHalfWidth,
                logoScale,
                LOGO_TEXTURE_SIZE,
                LOGO_HALF_HEIGHT,
                LOGO_TEXTURE_SIZE,
                LOGO_TEXTURE_SIZE,
                logoTint
        );
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                SplashOverlay.LOGO,
                centerX,
                centerY - logoHalfHeight,
                0.0625F,
                60.0F,
                logoHalfWidth,
                logoScale,
                LOGO_TEXTURE_SIZE,
                LOGO_HALF_HEIGHT,
                LOGO_TEXTURE_SIZE,
                LOGO_TEXTURE_SIZE,
                logoTint
        );

        renderProgressBar(context, width, height, MathHelper.clamp(progress, 0.0F, 1.0F), clampedAlpha);
    }

    private static void renderProgressBar(DrawContext context, int width, int height, float progress, float alpha) {
        int halfLogoWidth = (int) ((Math.min(width * 0.75D, height) * 0.25D) * 2.0D);
        int barY = (int) (height * 0.8325D);
        int minX = width / 2 - halfLogoWidth;
        int maxX = width / 2 + halfLogoWidth;
        int minY = barY - 5;
        int maxY = barY + 5;

        int lineColor = withAlpha(0xFFFFFF, alpha);
        int fillWidth = Math.round((maxX - minX - 4) * progress);

        context.fill(minX + 2, minY + 2, minX + 2 + fillWidth, maxY - 2, lineColor);
        context.fill(minX + 1, minY, maxX - 1, minY + 1, lineColor);
        context.fill(minX + 1, maxY - 1, maxX - 1, maxY, lineColor);
        context.fill(minX, minY, minX + 1, maxY, lineColor);
        context.fill(maxX - 1, minY, maxX, maxY, lineColor);
    }

    private static int withAlpha(int rgb, float alpha) {
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (resolvedAlpha << 24) | (rgb & 0xFFFFFF);
    }
}
