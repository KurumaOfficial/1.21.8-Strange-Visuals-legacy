package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.impl.interfaces.ClickGui;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.module.impl.other.Optimization;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

public final class InventoryHudRenderer {

    public static final float W = 164f;
    public static final float H = 74f;

    private final WaterMark owner;
    private float fadeAlpha = 0f;

    public InventoryHudRenderer(WaterMark owner, Identifier inventoryIcon) {
        this.owner = owner;
    }

    public void render(DrawContext ctx, float x, float y) {
        fadeAlpha = Math.min(1.0f, fadeAlpha + 0.08f);

        Theme theme = ThemeManager.getTheme();
        boolean transparent = theme == Theme.TRANSPARENT_BLACK || theme == Theme.TRANSPARENT_WHITE;
        boolean neon = theme == Theme.NEON;

        ClickGui clickGui = ClickGui.getInstance();
        boolean glass = clickGui != null && clickGui.isGlassEnabled();
        boolean skipBg = !glass && Optimization.shouldSkipHudBlur();

        if (glass) {
            float glassAlpha = Math.max(0.0f, Math.min(1f, clickGui.getGlassAlpha() * fadeAlpha));
            int tint = 0xFFEAF2FA;
            RenderUtil.LiquidGlass.draw(ctx, x, y, W, H, 3f, tint, clickGui.getGlassBlur(), glassAlpha);
        } else if (!skipBg && neon) {
            int neonBg = RenderUtil.ColorUtil.replAlpha(0xFF1A0033, (int)(255f * fadeAlpha));
            RenderUtil.Round.draw(ctx, x, y, W, H, 3f, neonBg);
            int borderColor = RenderUtil.ColorUtil.replAlpha(0xFF7B2FFF, (int)(100f * fadeAlpha));
            RenderUtil.Border.draw(ctx, x, y, W, H, 3f, 1f, borderColor);
        } else if (!skipBg) {
            int baseColor = RenderUtil.ColorUtil.getBackGroundColor(1, 1);
            int fadedColor = RenderUtil.ColorUtil.multAlpha(baseColor, fadeAlpha);
            RenderUtil.Round.draw(ctx, x, y, W, H, 3f, fadedColor);
        }

        int textColor = transparent || neon
            ? RenderUtil.ColorUtil.getTextColor(1, 1)
            : RenderUtil.ColorUtil.getTextColor(1, 1);
        int fadedTextColor = RenderUtil.ColorUtil.multAlpha(textColor, fadeAlpha);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, ModLocalization.raw("Инвентарь"),
                x + 7f, y + 8.8f, 5,
                fadedTextColor);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 9 + row * 9 + col;
                ItemStack stack = owner.mc.player.getInventory().getStack(index);
                float sx = x + 5f + col * 17f;
                float sy = y + 16f + row * 17f;
                drawSlot(ctx, sx, sy, stack);
            }
        }

        if (isAreaEmpty()) {
            float gx = x + 5f;
            float gy = y + 16f;
            float gw = 16f + 8f * 17f;
            float gh = 16f + 2f * 17f;
            drawOverlayText(ctx, ModLocalization.tr("common.empty"), gx + gw / 2f, gy + gh / 2f + 5f, 11);
        }
    }

    private void drawSlot(DrawContext ctx, float x, float y, ItemStack stack) {
        int slotColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 8);
        int fadedSlotColor = RenderUtil.ColorUtil.multAlpha(slotColor, fadeAlpha);
        RenderUtil.Round.draw(ctx, x, y, 16f, 16f, 5f, fadedSlotColor);

        if (stack != null && !stack.isEmpty()) {
            int ix = (int) x;
            int iy = (int) y;
            ctx.drawItem(stack, ix, iy);
            ctx.drawStackOverlay(owner.mc.textRenderer, stack, ix, iy, null);
        }
    }

    private void drawOverlayText(DrawContext ctx, String text, float cx, float y, int size) {
        float w = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, size);
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
}