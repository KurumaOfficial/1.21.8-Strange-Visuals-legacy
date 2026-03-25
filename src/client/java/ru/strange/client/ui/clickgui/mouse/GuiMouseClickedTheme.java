package ru.strange.client.ui.clickgui.mouse;

import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.ui.clickgui.localization.GuiLanguage;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;
import ru.strange.client.ui.clickgui.render.GuiRenderTheme;

public class GuiMouseClickedTheme extends GuiScreen {
    private static final float START_X_OFFSET = 7.0f;
    private static final float START_Y_OFFSET = 64.0f;
    private static final float CARD_WIDTH = 102.0f;
    private static final float CARD_HEIGHT = 26.0f;
    private static final float CARD_SPACING = 8.0f;
    private static final float COLUMN_SPACING = 8.0f;

    public static boolean clickedTheme(double mouseX, double mouseY) {
        if (selectedCategories != Category.Theme) {
            return false;
        }

        float startX = x + START_X_OFFSET;
        float startY = y + START_Y_OFFSET;

        for (int index = 0; index < themes.length; index++) {
            Theme theme = themes[index];
            int row = index / 2;
            int col = index % 2;

            float drawX = startX + col * (CARD_WIDTH + COLUMN_SPACING);
            float drawY = startY + row * (CARD_HEIGHT + CARD_SPACING);

            if (isHovered(mouseX, mouseY, drawX, drawY, CARD_WIDTH, CARD_HEIGHT)) {
                ThemeManager.setTheme(theme);
                return true;
            }
        }

        float panelX = x + START_X_OFFSET;
        float panelY = GuiRenderTheme.getLanguagePanelY();
        float buttonWidth = 56.0f;
        float buttonHeight = 14.0f;
        float gap = 6.0f;
        float buttonsX = panelX + 211.0f - (buttonWidth * 2.0f + gap) - 7.0f;
        float buttonsY = panelY + 6.0f;

        if (isHovered(mouseX, mouseY, buttonsX, buttonsY, buttonWidth, buttonHeight)) {
            GuiLocalization.setLanguage(GuiLanguage.RU);
            return true;
        }

        if (isHovered(mouseX, mouseY, buttonsX + buttonWidth + gap, buttonsY, buttonWidth, buttonHeight)) {
            GuiLocalization.setLanguage(GuiLanguage.EN);
            return true;
        }

        return false;
    }
}
