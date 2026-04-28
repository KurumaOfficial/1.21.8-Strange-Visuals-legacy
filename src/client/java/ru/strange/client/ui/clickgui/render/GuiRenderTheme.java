package ru.strange.client.ui.clickgui.render;

import net.minecraft.client.gui.DrawContext;
import ru.strange.client.Strange;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.ui.clickgui.localization.GuiLanguage;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class GuiRenderTheme extends GuiScreen {
    private static final Map<Theme, Float> hoverAnimations = new HashMap<>();
    private static final Map<Theme, Float> selectAnimations = new HashMap<>();
    private static final float ANIMATION_SPEED = 0.15f;

    private static final float START_X_OFFSET = 7.0f;
    private static final float START_Y_OFFSET = 64.0f;
    private static final float CARD_WIDTH = 102.0f;
    private static final float CARD_HEIGHT = 26.0f;
    private static final float CARD_SPACING = 8.0f;
    private static final float COLUMN_SPACING = 8.0f;

    static {
        for (Theme theme : Theme.values()) {
            hoverAnimations.put(theme, 0f);
            selectAnimations.put(theme, 0f);
        }
    }

    public static void renderTheme(DrawContext ctx) {
        if (selectedCategories != Category.Theme) {
            return;
        }

        boolean transparentTheme = ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE
                || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK
                || ThemeManager.getTheme() == Theme.PURPLE
                || ThemeManager.getTheme() == Theme.PINK;
        boolean blackTheme = ThemeManager.getTheme() == Theme.BLACK;

        float mouseX = getScaledMouseX();
        float mouseY = getScaledMouseY();
        float startX = x + START_X_OFFSET;
        float startY = y + START_Y_OFFSET;

        for (int index = 0; index < themes.length; index++) {
            Theme theme = themes[index];
            int row = index / 2;
            int col = index % 2;

            float drawX = startX + col * (CARD_WIDTH + COLUMN_SPACING);
            float drawY = startY + row * (CARD_HEIGHT + CARD_SPACING);

            float targetHover = isHovered(mouseX, mouseY, drawX, drawY, CARD_WIDTH, CARD_HEIGHT) ? 1f : 0f;
            float currentHover = hoverAnimations.get(theme) + (targetHover - hoverAnimations.get(theme)) * ANIMATION_SPEED;
            hoverAnimations.put(theme, currentHover);

            float targetSelect = ThemeManager.getTheme() == theme ? 1f : 0f;
            float currentSelect = selectAnimations.get(theme) + (targetSelect - selectAnimations.get(theme)) * ANIMATION_SPEED;
            selectAnimations.put(theme, currentSelect);

            float finalDrawY = drawY - currentHover * 2.0f;
            int borderAlpha = (int) (80 + currentSelect * 100 + currentHover * 50);
            Color borderColor = blackTheme
                    ? new Color(255, 255, 255, Math.min(255, borderAlpha))
                    : transparentTheme
                    ? new Color(0, 0, 0, Math.min(255, borderAlpha / 2))
                    : new Color(0, 0, 0, Math.min(255, borderAlpha));

            RenderUtil.Border.draw(ctx, drawX, finalDrawY, CARD_WIDTH, CARD_HEIGHT, 2.5f,
                    0.1f + currentSelect * 0.5f, borderColor.getRGB());

            int backgroundAlpha = (int) (57 + currentHover * 30);
            RenderUtil.Round.draw(
                    ctx,
                    drawX,
                    finalDrawY,
                    CARD_WIDTH,
                    CARD_HEIGHT,
                    2.5f,
                    transparentTheme
                            ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), Math.min(255, backgroundAlpha))
                            : RenderUtil.ColorUtil.getBackGroundColor(1, 1)
            );

            RenderUtil.Image.draw(
                    ctx,
                    Strange.id("textures/theme/" + theme.toString().toLowerCase() + ".png"),
                    drawX - 0.5f,
                    finalDrawY,
                    CARD_WIDTH + 1.0f,
                    14.0f,
                    new Color(255, 255, 255, (int) (255 * (0.8f + currentHover * 0.2f)))
            );

            int textAlpha = (int) (RenderUtil.ColorUtil.getAlpha(RenderUtil.ColorUtil.getTextColor(1, 1)) * (0.9f + currentHover * 0.1f));
            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    theme.getName(),
                    drawX + 5.0f,
                    finalDrawY + 20.0f,
                    6,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), textAlpha)
            );

            if (currentSelect > 0.01f) {
                RenderUtil.Round.draw(
                        ctx,
                        drawX + CARD_WIDTH - 10.0f,
                        finalDrawY + 4.0f,
                        6.0f,
                        6.0f,
                        3.0f,
                        RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), (int) (255 * currentSelect))
                );
            }
        }

        renderLanguagePanel(ctx, mouseX, mouseY, transparentTheme, blackTheme);
    }

    private static void renderLanguagePanel(DrawContext ctx, float mouseX, float mouseY, boolean transparentTheme, boolean blackTheme) {
        Theme currentTheme = ThemeManager.getTheme();
        boolean lightTheme = currentTheme == Theme.WHITE || currentTheme == Theme.TRANSPARENT_WHITE;
        float panelX = x + START_X_OFFSET;
        float panelY = getLanguagePanelY();
        float panelWidth = 211.0f;
        float panelHeight = 26.0f;

        int backgroundColor = lightTheme
                ? new Color(255, 255, 255, transparentTheme ? 172 : 236).getRGB()
                : transparentTheme
                ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 88)
                : RenderUtil.ColorUtil.getBackGroundColor(1, 1);
        int borderColor = lightTheme
                ? new Color(0, 0, 0, transparentTheme ? 28 : 46).getRGB()
                : blackTheme
                ? new Color(255, 255, 255, 36).getRGB()
                : new Color(0, 0, 0, transparentTheme ? 36 : 64).getRGB();

        RenderUtil.Round.draw(ctx, panelX, panelY, panelWidth, panelHeight, 4.0f, backgroundColor);
        RenderUtil.Border.draw(ctx, panelX, panelY, panelWidth, panelHeight, 4.0f, 0.5f, borderColor);

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                GuiLocalization.tr("gui.lang.title"),
                panelX + 7.0f,
                panelY + 16.0f,
                5,
                RenderUtil.ColorUtil.getTextColor(1, 1)
        );

        float buttonWidth = 56.0f;
        float buttonHeight = 14.0f;
        float gap = 6.0f;
        float buttonsX = panelX + panelWidth - (buttonWidth * 2.0f + gap) - 7.0f;
        float buttonsY = panelY + 6.0f;

        drawLanguageButton(ctx, mouseX, mouseY, buttonsX, buttonsY, buttonWidth, buttonHeight,
                GuiLanguage.RU, GuiLocalization.tr("gui.lang.ru"), transparentTheme, lightTheme);
        drawLanguageButton(ctx, mouseX, mouseY, buttonsX + buttonWidth + gap, buttonsY, buttonWidth, buttonHeight,
                GuiLanguage.EN, GuiLocalization.tr("gui.lang.en"), transparentTheme, lightTheme);
    }

    private static void drawLanguageButton(DrawContext ctx, float mouseX, float mouseY, float x, float y, float width,
                                           float height, GuiLanguage language, String label, boolean transparentTheme,
                                           boolean lightTheme) {
        boolean active = GuiLocalization.currentLanguage() == language;
        boolean hovered = isHovered(mouseX, mouseY, x, y, width, height);

        int background = active
                ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 148)
                : lightTheme
                ? new Color(255, 255, 255, hovered ? 244 : 228).getRGB()
                : transparentTheme
                ? new Color(0, 0, 0, hovered ? 62 : 42).getRGB()
                : new Color(18, 18, 18, hovered ? 224 : 198).getRGB();
        int border = active
                ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 200)
                : lightTheme
                ? new Color(0, 0, 0, hovered ? 42 : 26).getRGB()
                : new Color(255, 255, 255, hovered ? 28 : 16).getRGB();
        int textColor = active
                ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 255)
                : lightTheme
                ? new Color(17, 19, 24, hovered ? 255 : 226).getRGB()
                : RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), hovered ? 232 : 210);

        RenderUtil.Round.draw(ctx, x, y, width, height, 4.0f, background);
        RenderUtil.Border.draw(ctx, x, y, width, height, 4.0f, 0.45f, border);
        FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, label, x + width / 2.0f, y + 9.0f, 5, textColor, false);
    }

    public static float getLanguagePanelY() {
        int rows = (int) Math.ceil(themes.length / 2.0);
        return y + START_Y_OFFSET + rows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING + 6.0f;
    }

    private static float getScaledMouseX() {
        return (float) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth());
    }

    private static float getScaledMouseY() {
        return (float) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight());
    }
}
