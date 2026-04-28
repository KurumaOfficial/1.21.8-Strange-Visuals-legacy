package ru.strange.client.ui.clickgui.mouse;

import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.math.MathHelper;

public class GuiMouseClicked extends GuiScreen {
    public static boolean mouseClickedGui(double pMouseX, double pMouseY, int pButton) {
        x = (int) MathHelper.clamp(GuiScreen.x, 0, mc.getWindow().getScaledWidth() - width);
        y = (int) MathHelper.clamp(GuiScreen.y, 0, mc.getWindow().getScaledHeight() - height);

        if (GuiMouseClickedCategory.clickedCategory(pMouseX, pMouseY)) {
            return true;
        }
        if (GuiMouseClickedModule.clickedModule(pMouseX, pMouseY, pButton)) {
            return true;
        }

        if (GuiMouseClickedTheme.clickedTheme(pMouseX, pMouseY)) {
            return true;
        }

        return false;
    }
}
