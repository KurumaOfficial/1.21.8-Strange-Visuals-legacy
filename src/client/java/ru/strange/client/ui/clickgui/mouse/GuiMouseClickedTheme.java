package ru.strange.client.ui.clickgui.mouse;

import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.ui.clickgui.GuiScreen;

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
                resetTransientInteractionState();
                ThemeManager.setTheme(theme);
                return true;
            }
        }

        return false;
    }
}
