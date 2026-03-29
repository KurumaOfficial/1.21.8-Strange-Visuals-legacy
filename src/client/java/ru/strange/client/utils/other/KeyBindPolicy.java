package ru.strange.client.utils.other;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.BindSettings;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyBindPolicy {
    private static final Set<Integer> LOGGED_BIND_NAME_FAILURES = ConcurrentHashMap.newKeySet();

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
        if (key == 0 || key == BindSettings.NONE) {
            return BindSettings.NONE;
        }

        if (BindSettings.isMouseCode(key)) {
            return key;
        }

        if (key < 0 || isProtectedFunctionKey(key)) {
            return BindSettings.NONE;
        }

        return key;
    }

    public static String getBindName(int key) {
        int normalized = normalizeStoredBind(key);
        if (normalized == BindSettings.NONE) {
            return "NONE";
        }

        if (BindSettings.isMouseCode(normalized)) {
            int button = BindSettings.toMouseButton(normalized);
            return switch (button) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "M1";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "M2";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "M3";
                case 3 -> "M4";
                case 4 -> "M5";
                case 5 -> "M6";
                case 6 -> "M7";
                case 7 -> "M8";
                default -> "M" + (button + 1);
            };
        }

        try {
            return InputUtil.fromKeyCode(normalized, -1).getLocalizedText().getString();
        } catch (RuntimeException exception) {
            if (LOGGED_BIND_NAME_FAILURES.add(normalized)) {
                Strange.LOGGER.warn("Failed to resolve key bind name for code {}", normalized, exception);
            }
            return "KEY_" + normalized;
        }
    }

    public static boolean sanitizeAllCustomBinds() {
        if (Strange.get == null || Strange.get.manager == null) {
            return false;
        }

        boolean changed = false;
        for (Module module : Strange.get.manager.getModules()) {
            int sanitizedModuleBind = normalizeStoredBind(module.bind);
            if (module.bind != sanitizedModuleBind) {
                module.setBindSilently(sanitizedModuleBind);
                changed = true;
            }

            for (Setting setting : module.getSettings()) {
                if (!(setting instanceof BindSettings bindSetting)) {
                    continue;
                }

                int sanitizedSettingBind = normalizeStoredBind(bindSetting.key);
                if (bindSetting.key != sanitizedSettingBind) {
                    bindSetting.setSilently(sanitizedSettingBind);
                    changed = true;
                }
            }
        }

        return changed;
    }
}
