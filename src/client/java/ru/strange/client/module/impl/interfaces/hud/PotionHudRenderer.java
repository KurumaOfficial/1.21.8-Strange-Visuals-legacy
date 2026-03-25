package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Рендерит столбцы активных зелий (иконка + вертикальная полоска + время).
 */
public final class PotionHudRenderer {

    public static final float CARD_W =  24f;
    public static final float CARD_H =  48f;
    public static final float GAP    =   4f;

    private final WaterMark owner;

    public PotionHudRenderer(WaterMark owner) {
        this.owner = owner;
    }

    // ── public API ────────────────────────────────────────────────────

    public void render(DrawContext ctx, float x, float y) {
        List<StatusEffectInstance> effects = getSortedEffects();
        boolean preview = effects.isEmpty() && owner.isEditing();
        int count = preview ? 3 : effects.size();
        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            Identifier iconTexture;
            String     time;
            float      progress;

            if (preview) {
                switch (i) {
                    case 0 -> { iconTexture = Identifier.of("minecraft", "textures/mob_effect/speed.png");      time = "0:01"; progress = 0.10f; }
                    case 1 -> { iconTexture = Identifier.of("minecraft", "textures/mob_effect/strength.png");   time = "0:16"; progress = 0.45f; }
                    default->  { iconTexture = Identifier.of("minecraft", "textures/mob_effect/jump_boost.png");time = "1:12"; progress = 0.85f; }
                }
            } else {
                StatusEffectInstance eff = effects.get(i);
                iconTexture = getPotionTexture(eff);
                time        = formatTime(eff);
                progress    = getProgress(eff);
            }

            drawCard(ctx, x + i * (CARD_W + GAP), y, iconTexture, time, progress);
        }
    }

    /** Суммарная ширина группы для drag-редактора. */
    public float getGroupWidth() {
        int count = getDisplayCount();
        if (count <= 0) return 0f;
        return count * CARD_W + (count - 1) * GAP;
    }

    public int getDisplayCount() {
        int count = owner.mc.player != null ? owner.mc.player.getStatusEffects().size() : 0;
        if (count <= 0 && owner.isEditing()) return 3;
        return count;
    }

    // ── private helpers ───────────────────────────────────────────────

    private void drawCard(DrawContext ctx, float x, float y,
                          Identifier iconTexture, String time, float progress) {
        RenderUtil.drawClientRect(ctx, x, y, CARD_W, CARD_H);

        int iconSize = 12;
        float iconX = x + CARD_W / 2f - iconSize / 2f;
        float iconY = y + 4f;
        drawEffectTexture(ctx, iconTexture, iconX, iconY, iconSize);

        float barW = 3f;
        float barH = 20f;
        float barX = x + CARD_W / 2f - barW / 2f;
        float barY = y + 18f;

        RenderUtil.Round.draw(ctx, barX, barY, barW, barH, 1f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 55));

        float fillH = barH * MathHelper.clamp(progress, 0f, 1f);
        RenderUtil.Round.draw(ctx, barX, barY + (barH - fillH), barW, fillH, 1f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 225));

        float tw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, time, 4);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, time,
                x + CARD_W / 2f - tw / 2f, y + 42.5f, 4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 215));
    }

    private void drawEffectTexture(DrawContext ctx, Identifier texture, float x, float y, int size) {
        try {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                    (int) x, (int) y, 0f, 0f, size, size, 18, 18, 18, 18);
        } catch (Exception ignored) {
            ctx.drawItem(Items.POTION.getDefaultStack(), (int) x, (int) y);
        }
    }

    private List<StatusEffectInstance> getSortedEffects() {
        List<StatusEffectInstance> effects = new ArrayList<>(owner.mc.player.getStatusEffects());
        effects.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
        return effects;
    }

    private Identifier getPotionTexture(StatusEffectInstance effect) {
        Identifier id   = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
        String     path = id == null ? "speed" : id.getPath();
        return Identifier.of("minecraft", "textures/mob_effect/" + path + ".png");
    }

    private String formatTime(StatusEffectInstance effect) {
        int duration = effect.getDuration() / 20;
        int minutes  = duration / 60;
        int seconds  = duration % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private float getProgress(StatusEffectInstance effect) {
        return MathHelper.clamp(effect.getDuration() / 20f / 90f, 0f, 1f);
    }
}
