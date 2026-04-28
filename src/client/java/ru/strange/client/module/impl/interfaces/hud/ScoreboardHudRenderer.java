package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import ru.strange.client.utils.render.RenderUtil;

/**
 * Хелпер для замены ванильного прямоугольного фона скорборда
 * на клиентский блок со скруглением и блюром.
 */
public final class ScoreboardHudRenderer {

    private ScoreboardHudRenderer() {}

    public static void drawBackground(DrawContext ctx, int left, int top, int right, int bottom) {
        int width = Math.max(0, right - left);
        int height = Math.max(0, bottom - top);
        if (width <= 0 || height <= 0) {
            return;
        }

        RenderUtil.drawClientRect(ctx, left, top, width, height);
    }
}
