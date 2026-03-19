package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

/**
 * HUD-элемент: координаты игрока.
 */
public class CoordsHud extends HudElement {

    private static final float COORDS_H = 18f;
    private static final float COORDS_MIN_W = 54f;
    private static final float COORDS_ICON_SPACE = 18f;

    private final Identifier coordsIcon = Identifier.of(Strange.rootRes, "/icons/gui/coord.png");

    @Override
    public void initPosition(int sw, int sh) {
        x = 12f;
        y = sh * 0.24f;
    }

    @Override
    public float getWidth() {
        return getCoordsHudWidth(getCoordsText());
    }

    @Override
    public float getHeight() {
        return COORDS_H;
    }

    @Override
    public void render(DrawContext ctx, boolean editing) {
        if (mc.player == null || mc.world == null) return;

        String text = getCoordsText();
        float w = getCoordsHudWidth(text);

        RenderUtil.drawClientRect(ctx, x, y, w, COORDS_H);

        float sepX = x + 13f;

        float iconSize = 8f;
        float iconX = x + (13f - iconSize) / 2f + 1.0f;
        float iconY = y + (COORDS_H - iconSize) / 2f;

        RenderUtil.Image.draw(
                ctx,
                coordsIcon,
                iconX,
                iconY,
                iconSize,
                iconSize,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220)
        );

        RenderUtil.Round.draw(
                ctx,
                sepX,
                y + 3.5f,
                1.0f,
                11f,
                0.5f,
                coordsSeparatorColor(1f)
        );

        float textW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 7);

        float textAreaX = sepX + 4f;
        float textAreaW = w - (textAreaX - x) - 4f;

        float textX = textAreaX + (textAreaW - textW) / 2f;
        float textY = y + 11.2f;

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                text,
                textX,
                textY,
                7,
                coordsTextColor(1f)
        );
    }

    private String getCoordsText() {
        if (mc.player == null) return "0, 0, 0";
        return mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
    }

    public float getCoordsHudWidth() {
        return getCoordsHudWidth(getCoordsText());
    }

    public float getCoordsHudWidth(String text) {
        float textW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 7);
        return textW + 22f;
    }

    private int coordsTextColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (230 * alpha));
    }

    private int coordsSeparatorColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (45 * alpha));
    }
}
