package ru.strange.client.ui.clickgui.mouse;

import ru.strange.client.Strange;
import ru.strange.client.module.api.Category;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.render.FontDraw;

public class GuiMouseClickedCategory extends GuiScreen {
    public static boolean clickedCategory(double mouseX, double mouseY) {
        float xGo = 0;
        for (Category category : categories) {
            if (isHovered(mouseX, mouseY, x + 10 + xGo, y + 31, FontDraw.getWidth(FontDraw.FontType.MEDIUM, category.getName(), 5) + 6, 10)) {
                if (selectedCategories == category) {

                } else {
                    selectedCategories = category;
                    GuiScreen.modules = Strange.get.manager.getType(GuiScreen.selectedCategories);
                    GuiScreen.scroll.reset();
                    if (Strange.get != null && Strange.get.configManager != null) {
                        Strange.get.configManager.autoSave();
                    }

                    return true;
                }
            }
            xGo += 9 + FontDraw.getWidth(FontDraw.FontType.MEDIUM, category.getName(), 5);
        }
        return false;
    }
}
