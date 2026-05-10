package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class CustomLoadingScreen {
    private static final int LOGO_TEXTURE_SIZE = 120;
    private static final int LOGO_HALF_HEIGHT = 60;
    private static final int MIN_BAR_HALF_WIDTH = 12;
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_PADDING = 2;
    private static final int GLOW_PADDING_X = 18;
    private static final int GLOW_PADDING_Y = 14;
    private static final int STATUS_TEXT_GAP = 12;
    private static final int PERCENT_TEXT_GAP = 4;

    private CustomLoadingScreen() {
    }

    public static void render(DrawContext context, int width, int height, float progress, long timeMs, float alpha, boolean reloading) {
        if (width <= 0 || height <= 0) {
            return;
        }

        float clampedAlpha = MathHelper.clamp(alpha, 0.0F, 1.0F);
        LoadingLayout layout = resolveLayout(width, height);
        float pulse = 0.5F + 0.5F * (float) Math.sin(timeMs / 420.0D);
        int logoTint = withAlpha(0xFFFFFF, clampedAlpha);

        renderBackground(context, width, height, clampedAlpha, reloading);
        renderLogoGlow(context, layout, clampedAlpha, pulse, reloading);

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                SplashOverlay.LOGO,
                layout.leftLogoX(),
                layout.logoY(),
                -0.0625F,
                0.0F,
                layout.logoHalfWidth(),
                layout.logoHeight(),
                LOGO_TEXTURE_SIZE,
                LOGO_HALF_HEIGHT,
                LOGO_TEXTURE_SIZE,
                LOGO_TEXTURE_SIZE,
                logoTint
        );
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                SplashOverlay.LOGO,
                layout.rightLogoX(),
                layout.logoY(),
                0.0625F,
                60.0F,
                layout.logoHalfWidth(),
                layout.logoHeight(),
                LOGO_TEXTURE_SIZE,
                LOGO_HALF_HEIGHT,
                LOGO_TEXTURE_SIZE,
                LOGO_TEXTURE_SIZE,
                logoTint
        );

        renderProgressBar(context, layout, MathHelper.clamp(progress, 0.0F, 1.0F), clampedAlpha, pulse, timeMs, reloading);
        renderStatusText(context, layout, MathHelper.clamp(progress, 0.0F, 1.0F), clampedAlpha, reloading);
    }

    private static void renderBackground(DrawContext context, int width, int height, float alpha, boolean reloading) {
        int topColor = withAlpha(reloading ? 0x060C14 : 0x05080E, alpha);
        int bottomColor = withAlpha(reloading ? 0x111B28 : 0x0D131B, alpha);
        context.fillGradient(0, 0, width, height, topColor, bottomColor);
    }

    private static void renderLogoGlow(DrawContext context, LoadingLayout layout, float alpha, float pulse, boolean reloading) {
        int glowAlpha = Math.round((reloading ? 34.0F : 24.0F) * alpha * (0.72F + pulse * 0.28F));
        if (glowAlpha <= 0) {
            return;
        }

        int glowColor = (glowAlpha << 24) | ((reloading ? 0x8EAEDD : 0xFFFFFF) & 0xFFFFFF);
        context.fill(
                layout.leftLogoX() - GLOW_PADDING_X,
                layout.logoY() - GLOW_PADDING_Y,
                layout.rightLogoX() + layout.logoHalfWidth() + GLOW_PADDING_X,
                layout.logoY() + layout.logoHeight() + GLOW_PADDING_Y,
                glowColor
        );
    }

    private static void renderProgressBar(DrawContext context, LoadingLayout layout, float progress, float alpha, float pulse, long timeMs, boolean reloading) {
        int minX = layout.barMinX();
        int maxX = layout.barMaxX();
        int minY = layout.barMinY();
        int maxY = layout.barMaxY();

        int frameColor = withAlpha(0xFFFFFF, alpha);
        int fillColor = withAlpha(reloading ? 0xB8D7FF : 0xFFFFFF, alpha);
        int shimmerColor = withAlpha(0xFFFFFF, alpha * (0.28F + 0.14F * pulse));
        int backgroundColor = withAlpha(0x1B2431, alpha * 0.9F);

        context.fill(minX, minY, maxX, maxY, backgroundColor);
        int innerWidth = Math.max(0, maxX - minX - BAR_PADDING * 2);
        int fillWidth = Math.round(innerWidth * progress);

        if (fillWidth > 0) {
            int fillStartX = minX + BAR_PADDING;
            int fillEndX = Math.min(maxX - BAR_PADDING, fillStartX + fillWidth);
            context.fill(fillStartX, minY + BAR_PADDING, fillEndX, maxY - BAR_PADDING, fillColor);

            int shimmerWidth = Math.max(6, Math.min(18, fillWidth / 3));
            if (fillWidth > shimmerWidth) {
                float phase = (float) ((timeMs % 1100L) / 1100.0D);
                int shimmerTravel = Math.max(0, fillWidth - shimmerWidth);
                int shimmerStart = fillStartX + Math.round(shimmerTravel * phase);
                context.fill(shimmerStart, minY + BAR_PADDING, Math.min(fillEndX, shimmerStart + shimmerWidth), maxY - BAR_PADDING, shimmerColor);
            }
        }

        int accentColor = withAlpha(reloading ? 0xC9DFFF : 0xFFFFFF, alpha * (0.55F + pulse * 0.25F));
        context.fill(minX + 1, minY, maxX - 1, minY + 1, accentColor);
        context.fill(minX + 1, maxY - 1, maxX - 1, maxY, frameColor);
        context.fill(minX, minY, minX + 1, maxY, frameColor);
        context.fill(maxX - 1, minY, maxX, maxY, frameColor);
    }

    private static void renderStatusText(DrawContext context, LoadingLayout layout, float progress, float alpha, boolean reloading) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        int statusColor = withAlpha(reloading ? 0xE3ECF9 : 0xEDF1F8, alpha);
        int percentColor = withAlpha(0xB8C3D4, alpha * 0.92F);
        String statusText = reloading ? "Reloading resources" : "Loading game";
        String percentText = Math.round(progress * 100.0F) + "%";

        int statusWidth = client.textRenderer.getWidth(statusText);
        int percentWidth = client.textRenderer.getWidth(percentText);

        context.drawTextWithShadow(
                client.textRenderer,
                Text.literal(statusText),
                layout.centerX() - statusWidth / 2,
                layout.statusTextY(),
                statusColor
        );
        context.drawTextWithShadow(
                client.textRenderer,
                Text.literal(percentText),
                layout.centerX() - percentWidth / 2,
                layout.percentTextY(),
                percentColor
        );
    }

    private static LoadingLayout resolveLayout(int width, int height) {
        int horizontalPadding = Math.max(2, Math.min(12, width / 10));
        int centerX = width / 2;
        int centerY = height / 2;
        int desiredLogoHeight = Math.max(1, (int) (Math.min(width * 0.75D, height) * 0.25D));
        int maxLogoHalfWidth = Math.max(1, (width - horizontalPadding * 2) / 2);
        int logoHalfWidth = Math.max(1, Math.min(maxLogoHalfWidth, desiredLogoHeight * 2));
        int logoHeight = Math.max(1, logoHalfWidth / 2);
        int logoHalfHeight = Math.max(1, logoHeight / 2);
        int maxBarHalfWidth = Math.max(1, (width - horizontalPadding * 2) / 2);
        int minBarHalfWidth = Math.min(MIN_BAR_HALF_WIDTH, maxBarHalfWidth);
        int barHalfWidth = Math.max(minBarHalfWidth, Math.min(logoHalfWidth, maxBarHalfWidth));
        int barCenterY = Math.max(horizontalPadding + logoHeight + STATUS_TEXT_GAP + 12, (int) (height * 0.8325D));
        int maxBarCenterY = Math.max(BAR_HEIGHT, height - horizontalPadding - BAR_HEIGHT / 2);
        barCenterY = Math.min(barCenterY, maxBarCenterY);
        int statusTextY = Math.max(horizontalPadding, centerY + logoHalfHeight + STATUS_TEXT_GAP);
        statusTextY = Math.min(statusTextY, barCenterY - BAR_HEIGHT / 2 - STATUS_TEXT_GAP);
        int percentTextY = Math.min(height - horizontalPadding - 9, barCenterY + BAR_HEIGHT / 2 + PERCENT_TEXT_GAP);
        return new LoadingLayout(
                centerX - logoHalfWidth,
                centerX,
                Math.max(horizontalPadding, centerY - logoHalfHeight),
                logoHalfWidth,
                logoHeight,
                centerX - barHalfWidth,
                centerX + barHalfWidth,
                barCenterY - BAR_HEIGHT / 2,
                barCenterY - BAR_HEIGHT / 2 + BAR_HEIGHT,
                centerX,
                statusTextY,
                percentTextY
        );
    }

    private static int withAlpha(int rgb, float alpha) {
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (resolvedAlpha << 24) | (rgb & 0xFFFFFF);
    }

    private record LoadingLayout(int leftLogoX, int rightLogoX, int logoY, int logoHalfWidth, int logoHeight,
                                 int barMinX, int barMaxX, int barMinY, int barMaxY,
                                 int centerX, int statusTextY, int percentTextY) {
    }
}
