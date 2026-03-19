package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

/**
 * HUD-элемент: сетка инвентаря (3×9).
 */
public class InventoryHud extends HudElement {

    public static final float W = 172f;
    public static final float H = 82f;

    private final Identifier inventoryIcon = Identifier.of(Strange.rootRes, "/icons/gui/invent.png");

    @Override
    public void initPosition(int sw, int sh) {
        x = sw - W - 14f;
        y = sh * 0.17f;
    }

    @Override
    public float getWidth() {
        return W;
    }

    @Override
    public float getHeight() {
        return H;
    }

    @Override
    public void render(DrawContext ctx, boolean editing) {
        if (mc.player == null || mc.world == null) return;

        drawInventoryCard(ctx, x, y, W, H);

        RenderUtil.Image.draw(
                ctx,
                inventoryIcon,
                x + 6f,
                y + 4f,
                12f,
                12f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220)
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                "Инвентарь",
                x + 24f,
                y + 11.1f,
                5,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 215)
        );

        RenderUtil.Round.draw(
                ctx,
                x + 6f,
                y + 18f,
                W - 12f,
                1f,
                0.5f,
                inventoryLineColor(1f)
        );

        int start = 9;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = start + row * 9 + col;
                ItemStack stack = mc.player.getInventory().getStack(index);

                float sx = x + 6f + col * 18f;
                float sy = y + 24f + row * 18f;

                drawInventorySlot(ctx, sx, sy, stack);
            }
        }
    }

    public boolean isInventoryAreaEmpty() {
        if (mc.player == null) return true;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void drawInventoryCard(DrawContext ctx, float x, float y, float w, float h) {
        RenderUtil.drawClientRect(ctx, x, y, w, h);
    }

    private void drawInventorySlot(DrawContext ctx, float x, float y, ItemStack stack) {
        RenderUtil.Round.draw(ctx, x, y, 16f, 16f, 5f, inventorySlotBackground(1f));

        if (stack != null && !stack.isEmpty()) {
            ctx.drawItem(stack, (int) x, (int) y);
        }
    }

    public void drawInventoryOverlayText(DrawContext ctx, String text, float centerX, float y, int size) {
        float width = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, size);
        float drawX = centerX - width / 2f;

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                text,
                drawX,
                y,
                size,
                inventoryOverlayColor(1f)
        );
    }

    private int inventoryLineColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (18 * alpha));
    }

    private int inventorySlotBackground(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (10 * alpha));
    }

    private int inventoryOverlayColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (70 * alpha));
    }
}
