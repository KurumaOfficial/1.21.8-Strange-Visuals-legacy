package ru.strange.client.ui.clickgui.mouse;

import ru.strange.client.module.api.Module;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.module.api.Category;

import java.util.List;

public class GuiMouseClickedModule extends GuiScreen {

    public static boolean clickedModule(double mouseX, double mouseY, int button) {
        if (selectedCategories == Category.Theme) {
            return false;
        }

        float yDown = 0.0F;
        float scrollY = scroll.getScroll();
        ModulePanel modulePanel = modulePanelBounds();
        float modulesX = modulePanel.x();
        float modulesY = modulePanel.y();
        float modulesHeight = modulePanel.height();
        float moduleWidth = modulePanel.width();
        float moduleHeaderHeight = moduleHeaderHeight();

        List<Module> visibleModules = visibleModules();

        for (Module module : visibleModules) {
            float up = calcUP(module);
            float drawY = modulesY + yDown + scrollY;
            float moduleBottom = drawY + moduleHeaderHeight + up;
            boolean headerVisible = drawY + moduleHeaderHeight > modulesY && drawY < modulesY + modulesHeight;
            boolean blockVisible = moduleBottom > modulesY && drawY < modulesY + modulesHeight;

            if (headerVisible && isHovered(mouseX, mouseY, modulesX, drawY, moduleWidth, moduleHeaderHeight)) {
                if (button == 0) {
                    module.toggle();
                    return true;
                }

                if (button == 1) {
                    module.open = !module.open;
                    return true;
                }

                if (button == 2) {
                    module.binding = true;
                    module.displayName = "Нажмите кнопку";
                    return true;
                }
            }

            if (!module.getSettingsForGUI().isEmpty() && module.open && blockVisible) {
                SettingsColumns settingsColumns = splitSettingsByColumn(module);
                if (GuiMouseClickedSettings.clickedSettings(settingsColumns.left(), mouseX, mouseY, modulesX, drawY + moduleHeaderHeight, modulesY, modulesY + modulesHeight)) {
                    return true;
                }

                if (GuiMouseClickedSettings.clickedSettings(settingsColumns.right(), mouseX, mouseY, x + 109.0F, drawY + moduleHeaderHeight, modulesY, modulesY + modulesHeight)) {
                    return true;
                }
            }

            yDown += moduleVerticalSpacing() + up;
        }

        return false;
    }
}
