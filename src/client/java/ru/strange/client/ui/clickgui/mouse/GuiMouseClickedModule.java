package ru.strange.client.ui.clickgui.mouse;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.render.FontDraw;

import java.util.ArrayList;

public class GuiMouseClickedModule extends GuiScreen {
    public static boolean clickedModule(double mouseX, double mouseY, int button) {

        float yDown = 0;
        float scrollY = scroll.getScroll();
        
        float modulesX = x + 7;
        float modulesY = y + 64;
        float modulesHeight = height - 64 - 7;

        for (Module module : modules) {
            float up = calcUP(module);

            float drawY = modulesY + yDown + scrollY;
            
            // Проверяем, что элемент видим в области скролла
            boolean isVisible = drawY + 26 > modulesY && drawY < modulesY + modulesHeight;

            if (isVisible && isHovered(mouseX, mouseY, modulesX, drawY, 211, 26)) {
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

            if (!module.getSettingsForGUI().isEmpty() && module.open && isVisible) {
                java.util.List<Setting> settings1 = new ArrayList<>();
                java.util.List<Setting> settings2 = new ArrayList<>();

                for (int i = 0; i < module.getSettingsForGUI().size(); i++) {
                    Setting setting = module.getSettingsForGUI().get(i);
                    if (i % 2 == 0) {
                        settings1.add(setting);
                    } else {
                        settings2.add(setting);
                    }
                }
                if (GuiMouseClickedSettings.clickedSettings(settings1, mouseX, mouseY, modulesX, drawY + 26)) {
                    return true;
                }
                if (GuiMouseClickedSettings.clickedSettings(settings2, mouseX, mouseY, x + 109, drawY + 26)) {
                    return true;
                }
            }

            yDown += 30 + up;
        }
        return false;
    }
}
