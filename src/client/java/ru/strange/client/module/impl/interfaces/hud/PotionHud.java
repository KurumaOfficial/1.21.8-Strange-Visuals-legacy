package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-элемент: активные эффекты зелий.
 */
public class PotionHud extends HudElement {

    /* ── константы ── */
    public static final float W   = 24f;
    public static final float H   = 48f;
    public static final float GAP = 4f;

    /* ── позиция по умолчанию ── */

    @Override
    public void initPosition(int sw, int sh) {
        x = sw - 96f;
        y = 8f;
    }

    /* ── размеры ── */

    @Override
    public float getWidth() {
        int count = getDisplayCount();
        if (count <= 0) return W;
        return count * W + (count - 1) * GAP;
    }

    @Override
    public float getHeight() {
        return H;
    }

    /* ── рендер ── */

    @Override
    public void render(DrawContext ctx, boolean editing) {
        this.editing = editing;
        renderPotionHud(ctx);
    }

    private void renderPotionHud(DrawContext ctx) {
        List<StatusEffectInstance> effects = getSortedEffects();

        boolean preview = effects.isEmpty() && editing;
        int count = preview ? 3 : effects.size();

        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            float px = x + i * (W + GAP);
            float py = y;

            Identifier iconTexture;
            String time;
            float progress;

            if (preview) {
                if (i == 0) {
                    iconTexture = Identifier.of("minecraft", "textures/mob_effect/speed.png");
                    time = "0:01";
                    progress = 0.10f;
                } else if (i == 1) {
                    iconTexture = Identifier.of("minecraft", "textures/mob_effect/strength.png");
                    time = "0:16";
                    progress = 0.45f;
                } else {
                    iconTexture = Identifier.of("minecraft", "textures/mob_effect/jump_boost.png");
                    time = "1:12";
                    progress = 0.85f;
                }
            } else {
                StatusEffectInstance effect = effects.get(i);
                iconTexture = getPotionTexture(effect);
                time = formatPotionTime(effect);
                progress = getPotionProgress(effect);
            }

            drawPotionCard(ctx, px, py, iconTexture, time, progress);
        }
    }

    /* ── вспомогательные методы ── */

    public List<StatusEffectInstance> getSortedEffects() {
        List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
        effects.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
        return effects;
    }

    private void drawPotionCard(DrawContext ctx, float cx, float cy, Identifier iconTexture, String time, float progress) {
        RenderUtil.drawClientRect(ctx, cx, cy, W, H);

        int iconSize = 12;
        float iconX = cx + W / 2f - iconSize / 2f;
        float iconY = cy + 4f;

        drawPotionEffectTexture(ctx, iconTexture, iconX, iconY, iconSize);

        float barW = 3f;
        float barH = 20f;
        float barX = cx + W / 2f - barW / 2f;
        float barY = cy + 18f;

        RenderUtil.Round.draw(
                ctx,
                barX,
                barY,
                barW,
                barH,
                1f,
                potionBarBackground(1f)
        );

        float fillH = barH * MathHelper.clamp(progress, 0f, 1f);

        RenderUtil.Round.draw(
                ctx,
                barX,
                barY + (barH - fillH),
                barW,
                fillH,
                1f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 225)
        );

        float tw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, time, 4);

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                time,
                cx + W / 2f - tw / 2f,
                cy + 42.5f,
                4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 215)
        );
    }

    private void drawPotionEffectTexture(DrawContext ctx, Identifier texture, float ex, float ey, int size) {
        try {
            ctx.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    (int) ex,
                    (int) ey,
                    0f,
                    0f,
                    size,
                    size,
                    18,
                    18,
                    18,
                    18
            );
        } catch (Throwable ignored) {
            ctx.drawItem(Items.POTION.getDefaultStack(), (int) ex, (int) ey);
        }
    }

    public Identifier getPotionTexture(StatusEffectInstance effect) {
        Identifier id = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
        String path = id == null ? "speed" : id.getPath();
        return Identifier.of("minecraft", "textures/mob_effect/" + path + ".png");
    }

    public String formatPotionTime(StatusEffectInstance effect) {
        int duration = effect.getDuration() / 20;
        int minutes = duration / 60;
        int seconds = duration % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    public float getPotionProgress(StatusEffectInstance effect) {
        float seconds = effect.getDuration() / 20f;
        return MathHelper.clamp(seconds / 90f, 0f, 1f);
    }

    public int getDisplayCount() {
        int count = mc.player != null ? mc.player.getStatusEffects().size() : 0;
        if (count <= 0 && editing) return 3;
        return count;
    }

    /* ── цвета ── */

    private int potionBarBackground(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (55 * alpha));
    }
}
