package ru.strange.client.ui.clickgui.mouse;

import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.other.ModuleVisibilityUtil;

import java.util.ArrayList;
import java.util.List;

public class GuiMouseClickedModule extends GuiScreen {

    private static final float MODULE_WIDTH = 211.0F;
    private static final float MODULE_HEADER_HEIGHT = 26.0F;

    public static boolean clickedModule(double mouseX, double mouseY, int button) {
        float yDown = 0.0F;
        float scrollY = scroll.getScroll();

        float modulesX = x + 7.0F;
        float modulesY = y + 64.0F;
        float modulesHeight = height - 64.0F - 7.0F;

        List<Module> visibleModules = new ArrayList<>();
        for (Module module : modules) {
            if (ModuleVisibilityUtil.shouldShow(module)) {
                visibleModules.add(module);
            }
        }

        for (Module module : visibleModules) {
            float up = calcUP(module);
            float drawY = modulesY + yDown + scrollY;
            float moduleBottom = drawY + MODULE_HEADER_HEIGHT + up;
            boolean headerVisible = drawY + MODULE_HEADER_HEIGHT > modulesY && drawY < modulesY + modulesHeight;
            boolean blockVisible = moduleBottom > modulesY && drawY < modulesY + modulesHeight;

            if (headerVisible && isHovered(mouseX, mouseY, modulesX, drawY, MODULE_WIDTH, MODULE_HEADER_HEIGHT)) {
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
                List<Setting> settings1 = new ArrayList<>();
                List<Setting> settings2 = new ArrayList<>();

                for (int i = 0; i < module.getSettingsForGUI().size(); ++i) {
                    Setting setting = module.getSettingsForGUI().get(i);
                    if (i % 2 == 0) {
                        settings1.add(setting);
                    } else {
                        settings2.add(setting);
                    }
                }

                if (GuiMouseClickedSettings.clickedSettings(settings1, mouseX, mouseY, modulesX, drawY + MODULE_HEADER_HEIGHT, modulesY, modulesY + modulesHeight)) {
                    return true;
                }

                if (GuiMouseClickedSettings.clickedSettings(settings2, mouseX, mouseY, x + 109.0F, drawY + MODULE_HEADER_HEIGHT, modulesY, modulesY + modulesHeight)) {
                    return true;
                }
            }

            yDown += 30.0F + up;
        }

        return false;
    }
}
