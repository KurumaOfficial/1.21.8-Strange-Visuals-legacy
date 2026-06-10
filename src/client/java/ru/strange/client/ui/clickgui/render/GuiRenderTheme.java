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
    private static float themePanelAnimation = 0f;
    private static final float ANIMATION_SPEED = 0.15f;
    private static final float PANEL_ANIMATION_SPEED = 0.12f;

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

    private static final float LANG_SWITCH_WIDTH = 56f;
    private static final float LANG_SWITCH_HEIGHT = 16f;
    private static final float LANG_SWITCH_OFFSET_RIGHT = 6f;
    private static final float LANG_SWITCH_OFFSET_TOP = -22f;
    private static final float LANG_BUTTON_GAP = 2f;

    public static void renderTheme(DrawContext ctx) {
        float targetAnimation = (selectedCategories == Category.Theme) ? 1f : 0f;
        themePanelAnimation += (targetAnimation - themePanelAnimation) * PANEL_ANIMATION_SPEED;

        if (themePanelAnimation < 0.01f) {
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

            float cardAnimation = Math.min(1f, themePanelAnimation * 2f - (index * 0.1f));
            cardAnimation = Math.max(0f, cardAnimation);
            float finalDrawY = drawY - currentHover * 2.0f + (1f - cardAnimation) * 30f;
            int borderAlpha = (int) (80 + currentSelect * 100 + currentHover * 50);
            Color borderColor = blackTheme
                    ? new Color(255, 255, 255, Math.min(255, borderAlpha))
                    : transparentTheme
                    ? new Color(0, 0, 0, Math.min(255, borderAlpha / 2))
                    : new Color(0, 0, 0, Math.min(255, borderAlpha));

            RenderUtil.Border.draw(ctx, drawX, finalDrawY, CARD_WIDTH, CARD_HEIGHT, 2.5f,
                    0.1f + currentSelect * 0.5f, borderColor.getRGB());

            int backgroundAlpha = (int) ((57 + currentHover * 30) * cardAnimation);
            RenderUtil.Round.draw(
                    ctx,
                    drawX,
                    finalDrawY,
                    CARD_WIDTH,
                    CARD_HEIGHT,
                    2.5f,
                    transparentTheme
                            ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), Math.min(255, backgroundAlpha))
                            : RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int) (255 * cardAnimation))
            );

            RenderUtil.Image.draw(
                    ctx,
                    Strange.id("textures/theme/" + theme.toString().toLowerCase() + ".png"),
                    drawX - 0.5f,
                    finalDrawY,
                    CARD_WIDTH + 1.0f,
                    14.0f,
                    new Color(255, 255, 255, (int) (255 * (0.8f + currentHover * 0.2f) * cardAnimation))
            );

            int textAlpha = (int) (RenderUtil.ColorUtil.getAlpha(RenderUtil.ColorUtil.getTextColor(1, 1)) * (0.9f + currentHover * 0.1f) * cardAnimation);
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
    }

    public static void renderLanguageSwitch(DrawContext ctx) {
        float mouseX = getScaledMouseX();
        float mouseY = getScaledMouseY();

        float switchX = x + width - LANG_SWITCH_WIDTH - LANG_SWITCH_OFFSET_RIGHT;
        float switchY = y + LANG_SWITCH_OFFSET_TOP;

        boolean dark = ThemeManager.getTheme() == Theme.BLACK || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK;
        int bgColor = dark ? new Color(18, 18, 18, 230).getRGB()
                : new Color(245, 245, 245, 230).getRGB();
        int borderColor = dark ? new Color(80, 80, 80, 180).getRGB()
                : new Color(180, 180, 180, 180).getRGB();

        RenderUtil.Round.draw(ctx, switchX, switchY, LANG_SWITCH_WIDTH, LANG_SWITCH_HEIGHT, 4f, bgColor);
        RenderUtil.Border.draw(ctx, switchX, switchY, LANG_SWITCH_WIDTH, LANG_SWITCH_HEIGHT, 4f, 0.5f, borderColor);

        float segW = (LANG_SWITCH_WIDTH - LANG_BUTTON_GAP) / 2f;
        float ruX = switchX;
        float enX = switchX + segW + LANG_BUTTON_GAP;

        boolean ruHover = isHovered(mouseX, mouseY, ruX, switchY, segW, LANG_SWITCH_HEIGHT);
        boolean enHover = isHovered(mouseX, mouseY, enX, switchY, segW, LANG_SWITCH_HEIGHT);
        boolean ruActive = GuiLocalization.currentLanguage() == GuiLanguage.RU;
        boolean enActive = GuiLocalization.currentLanguage() == GuiLanguage.EN;

        drawLangSegment(ctx, ruX, switchY, segW, LANG_SWITCH_HEIGHT, GuiLocalization.tr("gui.lang.ru"), ruActive, ruHover, dark);
        drawLangSegment(ctx, enX, switchY, segW, LANG_SWITCH_HEIGHT, GuiLocalization.tr("gui.lang.en"), enActive, enHover, dark);
    }

    private static void drawLangSegment(DrawContext ctx, float x, float y, float w, float h, String label, boolean active, boolean hovered, boolean dark) {
        int bg = active
                ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 180)
                : dark
                ? new Color(30, 30, 30, (hovered ? 200 : 160)).getRGB()
                : new Color(230, 230, 230, (hovered ? 220 : 180)).getRGB();
        int text = active
                ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 255)
                : dark
                ? new Color(200, 200, 200, (hovered ? 255 : 200)).getRGB()
                : new Color(40, 40, 40, (hovered ? 255 : 200)).getRGB();

        RenderUtil.Round.draw(ctx, x, y, w, h, 4f, bg);
        FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, label, x + w / 2f, y + h / 2f + 1f, 5, text, false);
    }

    public static boolean isLanguageSwitchHovered(double mouseX, double mouseY) {
        float switchX = x + width - LANG_SWITCH_WIDTH - LANG_SWITCH_OFFSET_RIGHT;
        float switchY = y + LANG_SWITCH_OFFSET_TOP;
        return isHovered(mouseX, mouseY, switchX, switchY, LANG_SWITCH_WIDTH, LANG_SWITCH_HEIGHT);
    }

    public static void handleLanguageSwitchClick(double mouseX, double mouseY) {
        float switchX = x + width - LANG_SWITCH_WIDTH - LANG_SWITCH_OFFSET_RIGHT;
        float switchY = y + LANG_SWITCH_OFFSET_TOP;
        float segW = (LANG_SWITCH_WIDTH - LANG_BUTTON_GAP) / 2f;
        float ruX = switchX;
        float enX = switchX + segW + LANG_BUTTON_GAP;

        if (isHovered(mouseX, mouseY, ruX, switchY, segW, LANG_SWITCH_HEIGHT)) {
            resetTransientInteractionState();
            GuiLocalization.setLanguage(GuiLanguage.RU);
        } else if (isHovered(mouseX, mouseY, enX, switchY, segW, LANG_SWITCH_HEIGHT)) {
            resetTransientInteractionState();
            GuiLocalization.setLanguage(GuiLanguage.EN);
        }
    }

    private static float getScaledMouseX() {
        return (float) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth());
    }

    private static float getScaledMouseY() {
        return (float) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight());
    }
}
