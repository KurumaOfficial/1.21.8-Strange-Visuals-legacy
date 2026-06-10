package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PotionHudRenderer {

    // ── Столбцы (режим 0) ─────────────────────────────────────────
    public static final float CARD_W = 24f;
    public static final float CARD_H = 48f;
    public static final float GAP    = 4f;

    // ── Список (режим 1) ──────────────────────────────────────────
    private static final float LIST_ROW_H        = 16f;
    private static final float LIST_HEADER_H     = 20f;
    private static final float LIST_PAD_X        = 8f;
    private static final float LIST_PAD_Y        = 5f;

    private static final float LIST_ICON_SIZE    = 10f;
    private static final float LIST_HEADER_ICON  = 10f;

    private static final float LIST_TEXT_SIZE    = 5f;
    private static final float LIST_HEADER_SIZE  = 6f;

    private static final float LIST_MIN_W        = 96f;
    private static final float NAME_ICON_GAP     = 3f;
    private static final float ICON_TIME_GAP     = 5f;
    private static final int EFFECT_TEXTURE_SIZE = 18;

    private static final String[][] LIST_PREVIEW = {
            {"speed",      "Скорость",      "3:42"},
            {"strength",   "Сила 2",        "0:16"},
            {"jump_boost", "Прыжок",        "1:12"}
    };

    private final WaterMark owner;
    private final Identifier potionIcon = Strange.id("icons/gui/potion.png");
    private final Map<String, Identifier> effectTextureCache = new HashMap<>();
    private final List<RenderedEffect> cachedEffects = new ArrayList<>();
    private final List<StatusEffectInstance> effectSortBuffer = new ArrayList<>();
    private float fadeAlpha = 0f;

    private float cachedListWidth = LIST_MIN_W;
    private long cachedEffectWorldTime = Long.MIN_VALUE;
    private int cachedPlayerId = Integer.MIN_VALUE;
    private int cachedEffectSignature = Integer.MIN_VALUE;

    /**
     * 0 = Столбцы
     * 1 = Список
     */
    private int mode = 0;

    public PotionHudRenderer(WaterMark owner) {
        this.owner = owner;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getMode() {
        return mode;
    }

    // ── public API ────────────────────────────────────────────────────

    public void render(DrawContext ctx, float x, float y) {
        fadeAlpha = Math.min(1.0f, fadeAlpha + 0.08f);
        
        if (mode == 1) {
            renderList(ctx, x, y);
        } else {
            renderColumns(ctx, x, y);
        }
    }

    public float getGroupWidth() {
        if (mode == 1) return getListWidth();
        return getColumnsWidth();
    }

    public float getGroupHeight() {
        if (mode == 1) return getListHeight();
        return CARD_H;
    }

    public int getDisplayCount() {
        int count = getPreparedEffects().size();
        if (count <= 0 && owner.isEditing()) {
            return mode == 1 ? LIST_PREVIEW.length : 3;
        }
        return count;
    }

    // ══════════════════════════════════════════════════════════════════
    //  РЕЖИМ 0: СТОЛБЦЫ
    // ══════════════════════════════════════════════════════════════════

    private void renderColumns(DrawContext ctx, float x, float y) {
        List<RenderedEffect> effects = getPreparedEffects();
        boolean preview = effects.isEmpty() && owner.isEditing();
        int count = preview ? 3 : effects.size();
        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            Identifier iconTexture;
            String time;
            float progress;

            if (preview) {
                switch (i) {
                    case 0  -> { iconTexture = effectTexture("speed");      time = "0:01"; progress = 0.10f; }
                    case 1  -> { iconTexture = effectTexture("strength");   time = "0:16"; progress = 0.45f; }
                    default -> { iconTexture = effectTexture("jump_boost"); time = "1:12"; progress = 0.85f; }
                }
            } else {
                RenderedEffect effect = effects.get(i);
                iconTexture = effect.texture;
                time = effect.time;
                progress = effect.progress;
            }

            drawColumnCard(ctx, x + i * (CARD_W + GAP), y, iconTexture, time, progress);
        }
    }

    private float getColumnsWidth() {
        int count = getDisplayCount();
        if (count <= 0) return 0f;
        return count * CARD_W + (count - 1) * GAP;
    }

    private void drawColumnCard(DrawContext ctx, float x, float y,
                                Identifier iconTexture, String time, float progress) {
        RenderUtil.drawClientRect(ctx, x, y, CARD_W, CARD_H);

        int iconSize = 12;
        float iconX = x + CARD_W / 2f - iconSize / 2f;
        float iconY = y + 4f;
        drawTexture(ctx, iconTexture, iconX, iconY, iconSize, iconSize);

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

    // ══════════════════════════════════════════════════════════════════
    //  РЕЖИМ 1: СПИСОК
    // ══════════════════════════════════════════════════════════════════

    private void renderList(DrawContext ctx, float x, float y) {
        List<RenderedEffect> effects = getPreparedEffects();
        boolean preview = effects.isEmpty() && owner.isEditing();
        int count = preview ? LIST_PREVIEW.length : effects.size();
        if (count <= 0) return;

        float totalW = getListWidth();
        float totalH = getListHeight();

        RenderUtil.drawClientRect(ctx, x, y, totalW, totalH);

        int headerColor = RenderUtil.ColorUtil.replAlpha(
                RenderUtil.ColorUtil.getTextColor(1, 1), 215);

        int lineColor = RenderUtil.ColorUtil.replAlpha(
                RenderUtil.ColorUtil.getTextColor(1, 1), 78);

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, ModLocalization.raw("Потионы"),
                x + LIST_PAD_X,
                y + LIST_PAD_Y + 10.8f,
                (int) LIST_HEADER_SIZE, headerColor);

        float headerIconX = x + totalW - LIST_PAD_X - LIST_HEADER_ICON;
        float headerIconY = y + LIST_PAD_Y + 2f;
        drawTexture(ctx, potionIcon, headerIconX, headerIconY,
                (int) LIST_HEADER_ICON, (int) LIST_HEADER_ICON);

        RenderUtil.Round.draw(ctx,
                x + LIST_PAD_X,
                y + LIST_HEADER_H,
                totalW - LIST_PAD_X * 2f,
                0.9f,
                0.4f,
                lineColor);

        float rowY = y + LIST_HEADER_H + LIST_PAD_Y;

        for (int i = 0; i < count; i++) {
            Identifier iconTexture;
            String name;
            String time;

            if (preview) {
                iconTexture = effectTexture(LIST_PREVIEW[i][0]);
                name = ModLocalization.raw(LIST_PREVIEW[i][1]);
                time = LIST_PREVIEW[i][2];
            } else {
                RenderedEffect effect = effects.get(i);
                iconTexture = effect.texture;
                name = effect.name;
                time = effect.time;
            }

            drawListRow(ctx, x, rowY, totalW, name, time, iconTexture);
            rowY += LIST_ROW_H;
        }
    }

    private void drawListRow(DrawContext ctx, float x, float y, float totalW,
                             String name, String time, Identifier icon) {

        int textColor = RenderUtil.ColorUtil.replAlpha(
                RenderUtil.ColorUtil.getTextColor(1, 1), 220);
        int mutedColor = RenderUtil.ColorUtil.replAlpha(
                RenderUtil.ColorUtil.getTextColor(1, 1), 170);

        float textY = y + LIST_ROW_H / 2f + 2.5f;

        float timeW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, time, (int) LIST_TEXT_SIZE);
        float timeX = x + totalW - LIST_PAD_X - timeW;

        float maxNameWidth = timeX - (x + LIST_PAD_X) - LIST_ICON_SIZE - NAME_ICON_GAP - ICON_TIME_GAP;
        if (maxNameWidth < 10f) maxNameWidth = 10f;

        String trimmedName = owner.trimToWidth(name, maxNameWidth, (int) LIST_TEXT_SIZE);

        float nameX = x + LIST_PAD_X;
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, trimmedName,
                nameX, textY,
                (int) LIST_TEXT_SIZE, textColor);

        float nameW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, trimmedName, (int) LIST_TEXT_SIZE);
        float iconX = nameX + nameW + NAME_ICON_GAP;
        float iconY = y + (LIST_ROW_H / 2f) - (LIST_ICON_SIZE / 2f);

        float maxIconX = timeX - ICON_TIME_GAP - LIST_ICON_SIZE;
        if (iconX > maxIconX) iconX = maxIconX;

        drawTexture(ctx, icon, iconX, iconY, (int) LIST_ICON_SIZE, (int) LIST_ICON_SIZE);

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, time,
                timeX, textY,
                (int) LIST_TEXT_SIZE, mutedColor);
    }

    private float getListWidth() {
        List<RenderedEffect> effects = getPreparedEffects();
        boolean preview = effects.isEmpty() && owner.isEditing();

        if (preview) {
            float maxNameW = 0f;
            float maxTimeW = 0f;
            for (String[] row : LIST_PREVIEW) {
                maxNameW = Math.max(maxNameW,
                        FontDraw.getWidth(FontDraw.FontType.MEDIUM, ModLocalization.raw(row[1]), (int) LIST_TEXT_SIZE));
                maxTimeW = Math.max(maxTimeW,
                        FontDraw.getWidth(FontDraw.FontType.MEDIUM, row[2], (int) LIST_TEXT_SIZE));
            }
            return computeListWidth(maxNameW, maxTimeW);
        }
        return cachedListWidth;
    }

    private float getListHeight() {
        int count = getDisplayCount();
        if (count <= 0) return 0f;
        return LIST_HEADER_H + LIST_PAD_Y + count * LIST_ROW_H + LIST_PAD_Y;
    }

    // ══════════════════════════════════════════════════════════════════
    //  ОБЩИЕ УТИЛИТЫ
    // ══════════════════════════════════════════════════════════════════

    private void drawTexture(DrawContext ctx, Identifier texture, float x, float y, int width, int height) {
        try {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                    (int) x, (int) y, 0f, 0f, width, height,
                    EFFECT_TEXTURE_SIZE, EFFECT_TEXTURE_SIZE, EFFECT_TEXTURE_SIZE, EFFECT_TEXTURE_SIZE);
        } catch (RuntimeException exception) {
            HudRenderDiagnostics.warnOnce("potion-hud-texture:" + texture, "Potion HUD texture fallback engaged", exception);
            ctx.drawItem(Items.POTION.getDefaultStack(), (int) x, (int) y);
        }
    }

    private List<RenderedEffect> getPreparedEffects() {
        refreshEffectCache();
        return cachedEffects;
    }

    private void refreshEffectCache() {
        if (Module.mc.player == null || Module.mc.world == null) {
            clearEffectCache();
            return;
        }

        Collection<StatusEffectInstance> rawEffects = Module.mc.player.getStatusEffects();
        long worldTime = Module.mc.world.getTime();
        int playerId = Module.mc.player.getId();
        int effectSignature = computeEffectSignature(rawEffects);

        if (worldTime == cachedEffectWorldTime
                && playerId == cachedPlayerId
                && effectSignature == cachedEffectSignature) {
            return;
        }

        effectSortBuffer.clear();
        effectSortBuffer.addAll(rawEffects);
        effectSortBuffer.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));

        cachedEffects.clear();
        float maxNameW = 0f;
        float maxTimeW = 0f;

        for (StatusEffectInstance effect : effectSortBuffer) {
            Identifier texture = getPotionTexture(effect);
            String name = getEffectName(effect);
            String time = formatTime(effect);
            float progress = getProgress(effect);

            cachedEffects.add(new RenderedEffect(texture, name, time, progress));
            maxNameW = Math.max(maxNameW,
                    FontDraw.getWidth(FontDraw.FontType.MEDIUM, name, (int) LIST_TEXT_SIZE));
            maxTimeW = Math.max(maxTimeW,
                    FontDraw.getWidth(FontDraw.FontType.MEDIUM, time, (int) LIST_TEXT_SIZE));
        }

        cachedListWidth = computeListWidth(maxNameW, maxTimeW);
        cachedEffectWorldTime = worldTime;
        cachedPlayerId = playerId;
        cachedEffectSignature = effectSignature;
    }

    private void clearEffectCache() {
        cachedEffects.clear();
        effectSortBuffer.clear();
        cachedListWidth = LIST_MIN_W;
        cachedEffectWorldTime = Long.MIN_VALUE;
        cachedPlayerId = Integer.MIN_VALUE;
        cachedEffectSignature = Integer.MIN_VALUE;
    }

    private int computeEffectSignature(Collection<StatusEffectInstance> effects) {
        int hash = effects.size();
        for (StatusEffectInstance effect : effects) {
            Identifier id = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
            int effectHash = id != null ? id.hashCode() : 0;
            effectHash = 31 * effectHash + effect.getAmplifier();
            effectHash = 31 * effectHash + (effect.isAmbient() ? 1 : 0);
            effectHash = 31 * effectHash + (effect.shouldShowIcon() ? 1 : 0);
            hash += effectHash;
            hash ^= Integer.rotateLeft(effectHash, 11);
        }
        return hash;
    }

    private float computeListWidth(float maxNameW, float maxTimeW) {
        float rowsW =
                LIST_PAD_X +
                        maxNameW +
                        NAME_ICON_GAP +
                        LIST_ICON_SIZE +
                        ICON_TIME_GAP +
                        maxTimeW +
                        LIST_PAD_X;

        float headerW =
                LIST_PAD_X +
                        FontDraw.getWidth(FontDraw.FontType.MEDIUM, ModLocalization.raw("Потионы"), (int) LIST_HEADER_SIZE) +
                        8f +
                        LIST_HEADER_ICON +
                        LIST_PAD_X;

        return Math.max(LIST_MIN_W, Math.max(rowsW, headerW));
    }

    private Identifier effectTexture(String name) {
        return effectTextureCache.computeIfAbsent(name,
                path -> Identifier.of("minecraft", "textures/mob_effect/" + path + ".png"));
    }

    private Identifier getPotionTexture(StatusEffectInstance effect) {
        Identifier id = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
        String path = id == null ? "speed" : id.getPath();
        return effectTexture(path);
    }

    private String getEffectName(StatusEffectInstance effect) {
        String name = effect.getEffectType().value().getName().getString();
        int amp = effect.getAmplifier();
        if (amp > 0) {
            name += " " + (amp + 1);
        }
        return ModLocalization.raw(name);
    }

    private String formatTime(StatusEffectInstance effect) {
        int duration = effect.getDuration() / 20;
        int minutes = duration / 60;
        int seconds = duration % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private float getProgress(StatusEffectInstance effect) {
        return MathHelper.clamp(effect.getDuration() / 20f / 90f, 0f, 1f);
    }

    private static final class RenderedEffect {
        private final Identifier texture;
        private final String name;
        private final String time;
        private final float progress;

        private RenderedEffect(Identifier texture, String name, String time, float progress) {
            this.texture = texture;
            this.name = name;
            this.time = time;
            this.progress = progress;
        }
    }
}
