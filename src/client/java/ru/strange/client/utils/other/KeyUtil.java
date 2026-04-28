package ru.strange.client.utils.other;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.utils.Helper;

public class KeyUtil implements Helper {

    public static boolean isKeyDown(int keyCode) {
        long handle = mc.getWindow().getHandle();

        if (keyCode == -1) {
            return false;
        }

        if (keyCode >= BindSettings.MOUSE_OFFSET) {
            int button = keyCode - BindSettings.MOUSE_OFFSET;
            return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
        }

        return InputUtil.isKeyPressed(handle, keyCode);
    }

    public static String getKey(int key) {
        if (key == -1) {
            return "null";
        }

        if (key >= BindSettings.MOUSE_OFFSET) {
            int button = key - BindSettings.MOUSE_OFFSET;
            return switch (button) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "Mouse Left";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "Mouse Right";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "Mouse Middle";
                case 3 -> "Mouse 4";
                case 4 -> "Mouse 5";
                case 5 -> "Mouse 6";
                case 6 -> "Mouse 7";
                case 7 -> "Mouse 8";
                default -> "Mouse " + (button + 1);
            };
        }

        if (key == 0) return "Mouse Left";
        if (key == 1) return "Mouse Right";
        if (key == 2) return "Mouse Middle";
        if (key == 3) return "Mouse 4";
        if (key == 4) return "Mouse 5";
        if (key == 32) return "Space";
        if (key == 39) return "Apostrophe";
        if (key == 44) return "Comma";
        if (key == 45) return "Minus";
        if (key == 46) return "Period";
        if (key == 47) return "Slash";
        if (key == 48) return "0";
        if (key == 49) return "1";
        if (key == 50) return "2";
        if (key == 51) return "3";
        if (key == 52) return "4";
        if (key == 53) return "5";
        if (key == 54) return "6";
        if (key == 55) return "7";
        if (key == 56) return "8";
        if (key == 57) return "9";
        if (key == 59) return "SemiColon";
        if (key == 61) return "Equal";
        if (key == 65) return "A";
        if (key == 66) return "B";
        if (key == 67) return "C";
        if (key == 68) return "D";
        if (key == 69) return "E";
        if (key == 70) return "F";
        if (key == 71) return "G";
        if (key == 72) return "H";
        if (key == 73) return "I";
        if (key == 74) return "J";
        if (key == 75) return "K";
        if (key == 76) return "L";
        if (key == 77) return "M";
        if (key == 78) return "N";
        if (key == 79) return "O";
        if (key == 80) return "P";
        if (key == 81) return "Q";
        if (key == 82) return "R";
        if (key == 83) return "S";
        if (key == 84) return "T";
        if (key == 85) return "U";
        if (key == 86) return "V";
        if (key == 87) return "W";
        if (key == 88) return "X";
        if (key == 89) return "Y";
        if (key == 90) return "Z";
        if (key == 91) return "LeftBracket";
        if (key == 92) return "BackSlash";
        if (key == 93) return "RightBracket";
        if (key == 96) return "GraveAccent";
        if (key == 161) return "World1";
        if (key == 162) return "World2";
        if (key == 256) return "Escape";
        if (key == 257) return "Enter";
        if (key == 258) return "Tab";
        if (key == 259) return "BackSpace";
        if (key == 260) return "Insert";
        if (key == 261) return "Delete";
        if (key == 262) return "Right";
        if (key == 263) return "Left";
        if (key == 264) return "Down";
        if (key == 265) return "Up";
        if (key == 266) return "PageUp";
        if (key == 267) return "PageDown";
        if (key == 268) return "Home";
        if (key == 269) return "End";
        if (key == 280) return "CapsLock";
        if (key == 281) return "ScrollLock";
        if (key == 282) return "NumLock";
        if (key == 283) return "PrintScreen";
        if (key == 284) return "Pause";
        if (key == 290) return "F1";
        if (key == 291) return "F2";
        if (key == 292) return "F3";
        if (key == 293) return "F4";
        if (key == 294) return "F5";
        if (key == 295) return "F6";
        if (key == 296) return "F7";
        if (key == 297) return "F8";
        if (key == 298) return "F9";
        if (key == 299) return "F10";
        if (key == 300) return "F11";
        if (key == 301) return "F12";
        if (key == 302) return "F13";
        if (key == 303) return "F14";
        if (key == 304) return "F15";
        if (key == 305) return "F16";
        if (key == 306) return "F17";
        if (key == 307) return "F18";
        if (key == 308) return "F19";
        if (key == 309) return "F20";
        if (key == 310) return "F21";
        if (key == 311) return "F22";
        if (key == 312) return "F23";
        if (key == 313) return "F24";
        if (key == 314) return "F25";
        if (key == 320) return "NUM 0";
        if (key == 321) return "NUM 1";
        if (key == 322) return "NUM 2";
        if (key == 323) return "NUM 3";
        if (key == 324) return "NUM 4";
        if (key == 325) return "NUM 5";
        if (key == 326) return "NUM 6";
        if (key == 327) return "NUM 7";
        if (key == 328) return "NUM 8";
        if (key == 329) return "NUM 9";
        if (key == 330) return "Decimal";
        if (key == 331) return "Divine";
        if (key == 332) return "Multiply";
        if (key == 333) return "Subtract";
        if (key == 334) return "Add";
        if (key == 335) return "Enter";
        if (key == 336) return "Equal";
        if (key == 340) return "LeftShift";
        if (key == 341) return "LeftControl";
        if (key == 342) return "LeftAlt";
        if (key == 343) return "LeftSuper";
        if (key == 344) return "RightShift";
        if (key == 345) return "RightControl";
        if (key == 346) return "RightAlt";
        if (key == 347) return "RightSuper";
        if (key == 348) return "Menu";

        return "null";
    }
}