package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

public final class ArmorHudRenderer {

    public static final float W = 80f;
    public static final float H = 22f;
    private static final int TEXT_SIZE = 6;

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final WaterMark owner;

    public ArmorHudRenderer(WaterMark owner) {
        this.owner = owner;
    }

    public void render(DrawContext ctx, float x, float y) {
        if (owner.mc.player == null) return;

        RenderUtil.drawClientRect(ctx, x, y, W, H);

        int textColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 230);
        float itemX = x + 3f;
        float itemY = y + 3f;

        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = owner.mc.player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, (int) itemX, (int) itemY);

                if (stack.isDamageable()) {
                    int maxDmg = stack.getMaxDamage();
                    int curDmg = maxDmg - stack.getDamage();
                    int percent = maxDmg > 0 ? (curDmg * 100 / maxDmg) : 100;

                    float barW = 16f;
                    float barH = 1.5f;
                    float barX = itemX;
                    float barY = itemY + 16.5f;
                    float fill = barW * (percent / 100f);

                    int barColor = getDurabilityColor(percent);
                    RenderUtil.Round.draw(ctx, barX, barY, barW, barH, 0.5f, 0x40000000);
                    RenderUtil.Round.draw(ctx, barX, barY, fill, barH, 0.5f, barColor);
                }
            }

            itemX += 19f;
        }
    }

    private static int getDurabilityColor(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        if (clamped >= 100) {
            return 0xFF8EEAFF;
        }
        if (clamped >= 50) {
            float t = (clamped - 50) / 50.0f;
            return RenderUtil.ColorUtil.interpolate(0xFFFFFF55, 0xFF55FF55, t);
        }
        float t = clamped / 50.0f;
        return RenderUtil.ColorUtil.interpolate(0xFFFF5555, 0xFFFFFF55, t);
    }
}
