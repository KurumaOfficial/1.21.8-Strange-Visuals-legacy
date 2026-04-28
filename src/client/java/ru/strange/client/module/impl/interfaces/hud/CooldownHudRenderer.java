package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.mixin.accessor.ItemCooldownEntryAccessor;
import ru.strange.client.mixin.accessor.ItemCooldownManagerAccessor;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CooldownHudRenderer {
    public static final float CARD_W = 92f;
    public static final float CARD_H = 20f;
    public static final float GAP = 4f;

    private record CooldownInfo(ItemStack stack, int remainingTicks, int totalTicks, float fillProgress) {}
    private record CooldownState(int remainingTicks, int totalTicks) {}

    private final WaterMark owner;

    public CooldownHudRenderer(WaterMark owner) {
        this.owner = owner;
    }

    public void render(DrawContext ctx, float x, float y) {
        List<CooldownInfo> infos = getCooldownInfos();
        boolean preview = infos.isEmpty() && owner.isEditing();
        int count = preview ? 2 : infos.size();
        if (count <= 0) {
            return;
        }

        for (int i = 0; i < count; i++) {
            float cx = x;
            float cy = y + i * (CARD_H + GAP);

            ItemStack icon;
            String timeText;
            float fill;

            if (preview) {
                if (i == 0) {
                    icon = new ItemStack(net.minecraft.item.Items.ENCHANTED_GOLDEN_APPLE);
                    timeText = ModLocalization.tr("hud.seconds", 128);
                    fill = 0.12f;
                } else {
                    icon = new ItemStack(net.minecraft.item.Items.ENDER_PEARL);
                    timeText = ModLocalization.tr("hud.seconds", 16);
                    fill = 0.76f;
                }
            } else {
                CooldownInfo info = infos.get(i);
                icon = info.stack();
                timeText = formatTime(info.remainingTicks());
                fill = info.fillProgress();
            }

            drawCard(ctx, cx, cy, icon, timeText, fill);
        }
    }

    public int getDisplayCount() {
        int count = getCooldownInfos().size();
        if (count <= 0 && owner.isEditing()) {
            return 2;
        }
        return count;
    }

    public float getGroupHeight() {
        int count = getDisplayCount();
        if (count <= 0) {
            return 0f;
        }
        return count * CARD_H + (count - 1) * GAP;
    }

    private void drawCard(DrawContext ctx, float x, float y, ItemStack icon, String timeText, float fillProgress) {
        RenderUtil.drawClientRect(ctx, x, y, CARD_W, CARD_H);

        if (icon != null && !icon.isEmpty()) {
            owner.targetRenderer.drawScaledItem(ctx, icon, x + 5f, y + 3f, 0.62f);
        }

        RenderUtil.Round.draw(ctx, x + 16f, y + 4f, 1.2f, 12f, 0.6f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 45));

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, timeText,
                x + 22f, y + 8.3f, 4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 230));

        float barX = x + 22f;
        float barY = y + 14.2f;
        float barW = CARD_W - 28f;
        float barH = 3f;
        float r = barH / 2f;

        RenderUtil.Round.draw(ctx, barX, barY, barW, barH, r,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 60));

        float fillW = barW * MathHelper.clamp(fillProgress, 0f, 1f);
        if (fillW > 0.01f) {
            fillW = Math.max(fillW, barH);
            RenderUtil.Round.draw(ctx, barX, barY, Math.min(fillW, barW), barH, r,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 235));
        }
    }

    private List<CooldownInfo> getCooldownInfos() {
        List<CooldownInfo> result = new ArrayList<>();
        if (owner.mc.player == null) {
            return result;
        }

        ItemCooldownManager manager = owner.mc.player.getItemCooldownManager();
        Map<Identifier, CooldownState> states = readCooldownStates(manager);
        if (states.isEmpty()) {
            return result;
        }

        Map<Identifier, ItemStack> matched = new LinkedHashMap<>();
        collectCooldownStack(matched, manager, states, owner.mc.player.getMainHandStack());
        collectCooldownStack(matched, manager, states, owner.mc.player.getOffHandStack());

        for (int i = 0; i < owner.mc.player.getInventory().size(); i++) {
            collectCooldownStack(matched, manager, states, owner.mc.player.getInventory().getStack(i));
        }

        for (Map.Entry<Identifier, ItemStack> entry : matched.entrySet()) {
            CooldownState state = states.get(entry.getKey());
            if (state == null || state.remainingTicks() <= 0 || state.totalTicks() <= 0) {
                continue;
            }

            float remainNorm = MathHelper.clamp(state.remainingTicks() / (float) state.totalTicks(), 0f, 1f);
            float fill = 1f - remainNorm;
            result.add(new CooldownInfo(entry.getValue(), state.remainingTicks(), state.totalTicks(), fill));
        }

        result.sort((a, b) -> Integer.compare(b.remainingTicks(), a.remainingTicks()));
        return result;
    }

    private void collectCooldownStack(Map<Identifier, ItemStack> out, ItemCooldownManager manager,
                                      Map<Identifier, CooldownState> states, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !manager.isCoolingDown(stack)) {
            return;
        }

        Identifier key = manager.getGroup(stack);
        if (key == null || !states.containsKey(key) || out.containsKey(key)) {
            return;
        }

        out.put(key, stack.copy());
    }

    private Map<Identifier, CooldownState> readCooldownStates(ItemCooldownManager manager) {
        Map<Identifier, CooldownState> result = new LinkedHashMap<>();
        ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) manager;

        int currentTick = accessor.getTick();
        for (Map.Entry<Identifier, Object> entry : accessor.getEntries().entrySet()) {
            if (!(entry.getValue() instanceof ItemCooldownEntryAccessor cooldownEntry)) {
                continue;
            }

            int startTick = cooldownEntry.getStartTick();
            int endTick = cooldownEntry.getEndTick();
            if (endTick <= startTick) {
                continue;
            }

            int remaining = Math.max(0, endTick - currentTick);
            int total = Math.max(1, endTick - startTick);
            if (remaining <= 0) {
                continue;
            }

            result.put(entry.getKey(), new CooldownState(remaining, total));
        }

        return result;
    }

    private static String formatTime(int remainingTicks) {
        int seconds = Math.max(1, (int) Math.ceil(remainingTicks / 20.0));
        return ModLocalization.tr("hud.seconds", seconds);
    }
}
