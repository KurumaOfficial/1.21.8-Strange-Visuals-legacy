package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

/**
 * Рендерит панель инвентаря (27 слотов, строки 1–3 инвентаря).
 */
public final class InventoryHudRenderer {

    public static final float W = 172f;
    public static final float H =  82f;

    private final WaterMark  owner;
    private final Identifier inventoryIcon;

    public InventoryHudRenderer(WaterMark owner, Identifier inventoryIcon) {
        this.owner         = owner;
        this.inventoryIcon = inventoryIcon;
    }

    // ── public API ────────────────────────────────────────────────────

    public void render(DrawContext ctx, float x, float y) {
        RenderUtil.drawClientRect(ctx, x, y, W, H);

        // ── header icon ──────────────────────────────────────────────
        RenderUtil.Image.draw(ctx, inventoryIcon,
                x + 6f, y + 4f, 12f, 12f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220));

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, ModLocalization.raw("Инвентарь"),
                x + 24f, y + 11.1f, 5,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 215));

        // ── divider ──────────────────────────────────────────────────
        RenderUtil.Round.draw(ctx, x + 6f, y + 18f, W - 12f, 1f, 0.5f,
                lineColor());

        // ── slots ────────────────────────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 9 + row * 9 + col;
                ItemStack stack = owner.mc.player.getInventory().getStack(index);
                float sx = x + 6f + col * 18f;
                float sy = y + 24f + row * 18f;
                drawSlot(ctx, sx, sy, stack);
            }
        }

        // ── empty overlay ────────────────────────────────────────────
        if (isAreaEmpty()) {
            float gx = x + 6f;
            float gy = y + 24f;
            float gw = 16f + 8f * 18f;
            float gh = 16f + 2f * 18f;
            drawOverlayText(ctx, ModLocalization.tr("common.empty"), gx + gw / 2f, gy + gh / 2f + 5f, 11);
        }
    }

    // ── private helpers ───────────────────────────────────────────────

    private void drawSlot(DrawContext ctx, float x, float y, ItemStack stack) {
        RenderUtil.Round.draw(ctx, x, y, 16f, 16f, 5f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 10));

        if (stack != null && !stack.isEmpty()) {
            ctx.drawItem(stack, (int) x, (int) y);
        }
    }

    private void drawOverlayText(DrawContext ctx, String text, float cx, float y, int size) {
        float w  = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, size);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, text,
                cx - w / 2f, y, size,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 70));
    }

    private boolean isAreaEmpty() {
        for (int i = 9; i < 36; i++) {
            ItemStack s = owner.mc.player.getInventory().getStack(i);
            if (s != null && !s.isEmpty()) return false;
        }
        return true;
    }

    // ── colors ────────────────────────────────────────────────────────
    private int lineColor() {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 18);
    }
}
