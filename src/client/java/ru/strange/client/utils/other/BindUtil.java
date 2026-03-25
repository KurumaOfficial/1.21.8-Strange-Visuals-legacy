package ru.strange.client.utils.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.module.api.setting.impl.BindSettings;

public class BindUtil {

    public static boolean isDown(int bindCode) {
        if (bindCode == -1) return false;

        long handle = MinecraftClient.getInstance().getWindow().getHandle();

        if (BindSettings.isMouseCode(bindCode)) {
            return GLFW.glfwGetMouseButton(handle, BindSettings.toMouseButton(bindCode)) == GLFW.GLFW_PRESS;
        }

        if (KeyBindPolicy.isProtectedFunctionKey(bindCode)) {
            return false;
        }

        return InputUtil.isKeyPressed(handle, bindCode);
    }

    public static boolean matchesMouse(int bindCode, int mouseButton) {
        return BindSettings.isMouseCode(bindCode) && BindSettings.toMouseButton(bindCode) == mouseButton;
    }

    public static boolean matchesKeyboard(int bindCode, int keyCode) {
        return !BindSettings.isMouseCode(bindCode)
                && !KeyBindPolicy.isProtectedFunctionKey(bindCode)
                && !KeyBindPolicy.isProtectedFunctionKey(keyCode)
                && bindCode == keyCode;
    }
}
