package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import ru.strange.client.utils.render.RenderUtil;

/**
 * Заменяет ванильный хотбар стилизованным фоном.
 * Вызывается из InGameHudMixin на TAIL renderMainHud:
 * рисует фон поверх ванильного хотбара и перерисовывает предметы сверху.
 */
public final class HotbarHudRenderer {

    private static final float SLOT = 20f;
    private static final float SLOT_BG = 22f;

    private HotbarHudRenderer() {}

    public static void renderOverlay(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int centerX = sw / 2;
        float x = centerX - 91f;
        float y = sh - 22f;
        Arm offHandArm = player.getMainArm().getOpposite();

        RenderUtil.drawClientRect(ctx, x, y, 182f, 22f);

        int selected = player.getInventory().getSelectedSlot();
        float selX = x + 1f + selected * SLOT;
        int border = 0xD8FFFFFF;
        int glow = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 185);
        int fill = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 95);
        RenderUtil.Round.draw(ctx, selX - 1f, y, SLOT + 2f, SLOT_BG, 3f, border);
        RenderUtil.Round.draw(ctx, selX - 0.5f, y + 0.5f, SLOT + 1f, SLOT + 1f, 2.5f, glow);
        RenderUtil.Round.draw(ctx, selX, y + 1f, SLOT, SLOT, 2f, fill);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                int ix = centerX - 90 + i * 20 + 2;
                int iy = sh - 19;
                ctx.drawItem(stack, ix, iy);
                ctx.drawStackOverlay(mc.textRenderer, stack, ix, iy);
            }
        }

        ItemStack offhand = player.getOffHandStack();
        if (!offhand.isEmpty()) {
            int itemX = offHandArm == Arm.LEFT ? centerX - 117 : centerX + 101;
            float offX = itemX - 3f;
            RenderUtil.drawClientRect(ctx, offX, y, SLOT_BG, SLOT_BG);
            ctx.drawItem(offhand, itemX, sh - 19);
            ctx.drawStackOverlay(mc.textRenderer, offhand, itemX, sh - 19);
        }

        if (mc.options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR) {
            float cooldown = mc.player.getAttackCooldownProgress(0.0f);
            if (cooldown < 1.0f) {
                int attackX = offHandArm == Arm.RIGHT ? centerX - 113 : centerX + 97;
                int attackY = sh - 20;
                RenderUtil.Round.draw(ctx, attackX, attackY, 18f, 18f, 3f,
                        RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 220));

                int progress = Math.max(0, Math.min(18, (int) (cooldown * 19.0f)));
                if (progress > 0) {
                    RenderUtil.Round.draw(ctx, attackX + 4f, attackY + (18 - progress), 10f, progress, 2f,
                            RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 210));
                }
            }
        }
    }
}
