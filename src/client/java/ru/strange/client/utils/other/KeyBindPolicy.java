package ru.strange.client.utils.other;

import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.BindSettings;

public final class KeyBindPolicy {

    private KeyBindPolicy() {
    }

    public static boolean isClearKey(int key) {
        return key == GLFW.GLFW_KEY_ESCAPE
                || key == GLFW.GLFW_KEY_DELETE
                || key == GLFW.GLFW_KEY_BACKSPACE;
    }

    public static boolean isProtectedFunctionKey(int key) {
        return key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12;
    }

    public static int normalizeStoredBind(int key) {
        if (key == BindSettings.NONE || BindSettings.isMouseCode(key)) {
            return key;
        }

        return isProtectedFunctionKey(key) ? BindSettings.NONE : key;
    }

    public static boolean sanitizeAllCustomBinds() {
        if (Strange.get == null || Strange.get.manager == null) {
            return false;
        }

        boolean changed = false;
        for (Module module : Strange.get.manager.getModules()) {
            int sanitizedModuleBind = normalizeStoredBind(module.bind);
            if (module.bind != sanitizedModuleBind) {
                module.bind = sanitizedModuleBind;
                changed = true;
            }

            for (Setting setting : module.getSettings()) {
                if (!(setting instanceof BindSettings bindSetting)) {
                    continue;
                }

                int sanitizedSettingBind = normalizeStoredBind(bindSetting.key);
                if (bindSetting.key != sanitizedSettingBind) {
                    bindSetting.key = sanitizedSettingBind;
                    changed = true;
                }
            }
        }

        return changed;
    }
}
