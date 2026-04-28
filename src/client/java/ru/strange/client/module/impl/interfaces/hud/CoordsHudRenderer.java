package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

/**
 * Рендерит панель координат (иконка + X, Y, Z).
 */
public final class CoordsHudRenderer {

    public static final float H        = 18f;
    private static final float MIN_W   = 54f;
    private static final float ICON_SPACE = 18f;
    private static final int   TEXT_SIZE  = 7;

    private final WaterMark  owner;
    private final Identifier coordsIcon;

    public CoordsHudRenderer(WaterMark owner, Identifier coordsIcon) {
        this.owner      = owner;
        this.coordsIcon = coordsIcon;
    }

    // ── public API ────────────────────────────────────────────────────

    public void render(DrawContext ctx, float x, float y) {
        String text = getCoordsText();
        float  w    = getWidth(text);

        RenderUtil.drawClientRect(ctx, x, y, w, H);

        float iconSize = 8f;
        float iconX    = x + (ICON_SPACE - iconSize) / 2f + 1f;
        float iconY    = y + (H - iconSize) / 2f;

        RenderUtil.Image.draw(ctx, coordsIcon, iconX, iconY, iconSize, iconSize,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220));

        float sepX = x + ICON_SPACE - 5f;
        RenderUtil.Round.draw(ctx, sepX, y + 3.5f, 1f, 11f, 0.5f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 45));

        float textW    = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, TEXT_SIZE);
        float textAreaX = sepX + 4f;
        float textAreaW = w - (textAreaX - x) - 4f;
        float textX    = textAreaX + (textAreaW - textW) / 2f;
        float textY    = y + 11.2f;

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, text, textX, textY, TEXT_SIZE,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 230));
    }

    public float getWidth() {
        return getWidth(getCoordsText());
    }

    // ── private helpers ───────────────────────────────────────────────

    private String getCoordsText() {
        if (owner.mc.player == null) return "0, 0, 0";
        return owner.mc.player.getBlockX() + ", "
             + owner.mc.player.getBlockY() + ", "
             + owner.mc.player.getBlockZ();
    }

    private float getWidth(String text) {
        float textW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, TEXT_SIZE);
        return Math.max(MIN_W, textW + ICON_SPACE + 8f);
    }
}
