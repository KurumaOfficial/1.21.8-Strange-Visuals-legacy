package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.utils.render.FontDraw;

/**
 * Базовый класс для всех перетаскиваемых HUD-элементов.
 * Хранит позицию, предоставляет общие утилиты рендеринга.
 */
public abstract class HudElement {

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    /** Позиция элемента (доступна drag-системе). */
    public float x, y;

    /** Текущее состояние режима редактирования (устанавливается оркестратором). */
    protected boolean editing;

    public abstract void render(DrawContext ctx, boolean editing);
    public abstract float getWidth();
    public abstract float getHeight();

    /** Начальная позиция при первом открытии. */
    public void initPosition(int sw, int sh) {}

    public void setEditing(boolean editing) {
        this.editing = editing;
    }

    /* ── общие утилиты ── */

    protected static float lerp(float from, float to, float speed) {
        speed = MathHelper.clamp(speed, 0f, 1f);
        return from + (to - from) * speed;
    }

    protected static float approach(float value, float target, float speed) {
        if (value < target) return Math.min(value + speed, target);
        return Math.max(value - speed, target);
    }

    protected static String trimToWidth(String text, float maxWidth, int size) {
        if (text == null) return "";
        if (FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, size) <= maxWidth) return text;
        String dots = "...";
        String result = text;
        while (!result.isEmpty()
                && FontDraw.getWidth(FontDraw.FontType.MEDIUM, result + dots, size) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + dots;
    }

    protected static String oneDecimal(float value) {
        int whole = (int) value;
        int decimal = (int) ((Math.abs(value - whole)) * 10f);
        return whole + "." + decimal;
    }

    protected static void drawScaledItem(DrawContext ctx, ItemStack stack, float x, float y, float scale) {
        if (stack == null || stack.isEmpty()) return;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawItem(stack, 0, 0);
        ctx.getMatrices().popMatrix();
    }

    public static boolean isMouseDown(int button) {
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }
}
