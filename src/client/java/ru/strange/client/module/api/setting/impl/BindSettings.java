package ru.strange.client.module.api.setting.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.utils.other.KeyBindPolicy;

import java.util.function.Supplier;

public class BindSettings extends Setting {

    public static final int NONE = -1;
    public static final int MOUSE_OFFSET = 1000;

    public int key;
    public String description;
    public boolean active;

    public BindSettings(String name, int key) {
        this.name = name;
        this.key = key;
        this.description = "";
    }

    public int get() {
        return key;
    }

    public void set(int key) {
        this.key = KeyBindPolicy.normalizeStoredBind(key);
        triggerAutoSave();
    }

    public void setSilently(int key) {
        this.key = KeyBindPolicy.normalizeStoredBind(key);
    }

    public BindSettings hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }

    public static int mouseCode(int button) {
        return MOUSE_OFFSET + button;
    }

    public static boolean isMouseCode(int code) {
        return code >= MOUSE_OFFSET;
    }

    public static int toMouseButton(int code) {
        return code - MOUSE_OFFSET;
    }

    public boolean isMouse() {
        return isMouseCode(this.key);
    }

    public boolean isKeyDown(int keyCode) {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();

        if (keyCode == NONE) {
            return false;
        }

        if (isMouseCode(keyCode)) {
            return GLFW.glfwGetMouseButton(handle, toMouseButton(keyCode)) == GLFW.GLFW_PRESS;
        }

        if (KeyBindPolicy.isProtectedFunctionKey(keyCode)) {
            return false;
        }

        return InputUtil.isKeyPressed(handle, keyCode);
    }

    public String getBindName() {
        if (key == NONE || KeyBindPolicy.isProtectedFunctionKey(key)) {
            return "NONE";
        }

        if (isMouse()) {
            int button = toMouseButton(key);
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
            return InputUtil.fromKeyCode(key, -1).getLocalizedText().getString();
        } catch (Exception e) {
            return "NONE";
        }
    }
}
