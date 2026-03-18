package ru.strange.client.ui.clickgui.mouse;

import net.minecraft.util.Identifier;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;

public class GuiMouseClickedTheme extends GuiScreen {
    public static boolean clickedTheme(double mouseX, double mouseY) {
        if (selectedCategories != Category.Theme) return false;

        float startX = x + 7;
        float startY = y + 64;
        float cardWidth = 102;
        float cardHeight = 26;
        float spacing = 8;
        float columnSpacing = 8;

        for (int index = 0; index < themes.length; index++) {
            Theme theme = themes[index];
            int row = index / 2;
            int col = index % 2;
            
            float drawX = startX + col * (cardWidth + columnSpacing);
            float drawY = startY + row * (cardHeight + spacing);

            if (isHovered(mouseX, mouseY, drawX, drawY, cardWidth, cardHeight)) {
                ThemeManager.setTheme(theme);
                return true;
            }
        }
        return false;
    }
}
