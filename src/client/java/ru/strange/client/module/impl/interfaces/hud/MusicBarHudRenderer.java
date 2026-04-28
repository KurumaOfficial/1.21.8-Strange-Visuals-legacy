package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.other.MediaUtil;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.Locale;

public final class MusicBarHudRenderer {

    // Общий габарит (для редактора/перетаскивания)
    public static final float W = 184.0f;
    public static final float H = 52.0f;

    private static final float HEADER_W = 144.0f;
    private static final float HEADER_H = 18.0f;

    private static final float CARD_W = 184.0f;
    private static final float CARD_H = 32.0f;
    private static final float CARD_GAP = 3.0f;

    private static final int CONTROL_ICON_TEX_SIZE = 24;
    private static final int PREV_ICON_TEX_SIZE = 32;

    private static final Identifier NOTE_ICON = Identifier.of("strange", "icons/gui/imag1e.png");
    private static final Identifier PREV_ICON = Identifier.of("strange", "icons/gui/forward2.png");
    private static final Identifier SKIP_ICON = Identifier.of("strange", "icons/gui/forward.png");
    private static final Identifier PLAY_ICON = Identifier.of("strange", "icons/gui/play.png");
    private static final Identifier PAUSE_ICON = Identifier.of("strange", "icons/gui/pause.png");

    private final WaterMark owner;

    private float[] waveHeights;
    private float[] waveTargets;
    private long lastWaveUpdate;

    private boolean expanded;
    private float expandAnim;

    private float headerX;
    private float headerY;
    private float headerW;
    private float headerH;

    private float prevButtonX;
    private float prevButtonY;
    private float prevButtonW;
    private float prevButtonH;

    private float playButtonX;
    private float playButtonY;
    private float playButtonW;
    private float playButtonH;

    private float nextButtonX;
    private float nextButtonY;
    private float nextButtonW;
    private float nextButtonH;

    private boolean controlsActive;

    public MusicBarHudRenderer(WaterMark owner) {
        this.owner = owner;
    }

    public void render(DrawContext ctx, float x, float y) {
        MediaUtil.MediaInfo mediaInfo = MediaUtil.getCurrentMedia();
        controlsActive = false;

        if (mediaInfo == null && !owner.isEditing()) {
            return;
        }

        String title = mediaInfo == null ? "No media" : safe(mediaInfo.title());
        String artist = mediaInfo == null ? "Waiting for player" : safe(mediaInfo.artist());
        String source = mediaInfo == null ? "" : safe(mediaInfo.sourceApp());

        if (artist.isBlank()) artist = source;
        if (title.isBlank()) title = "Unknown Track";

        boolean playing = mediaInfo != null && mediaInfo.playing();
        updateMusicWave(4, playing);

        expandAnim += ((expanded ? 1.0f : 0.0f) - expandAnim) * 0.22f;
        if (expandAnim < 0.001f) expandAnim = 0.0f;
        if (expandAnim > 0.999f) expandAnim = 1.0f;

        float openPc = easeOut(expandAnim);

        int screenWidth = ctx.getScaledWindowWidth();
        float cardX = owner.isEditing() ? x : (screenWidth - CARD_W) * 0.5f;
        float cardY = y + HEADER_H + CARD_GAP + (1.0f - openPc) * 4.0f;

        float topX = cardX + (CARD_W - HEADER_W) * 0.5f;
        float topY = y;

        headerX = topX;
        headerY = topY;
        headerW = HEADER_W;
        headerH = HEADER_H;

        // Header
        RenderUtil.drawClientRect(ctx, topX, topY, HEADER_W, HEADER_H);

        RenderUtil.Image.draw(
                ctx,
                NOTE_ICON,
                topX + 6.0f,
                topY + 4.6f,
                6.0f,
                9.0f,
                alpha(0xFFFFFFFF, 0.88f)
        );

        RenderUtil.Round.draw(ctx,
                topX + 18.5f, topY + 4.0f,
                1.0f, HEADER_H - 8.0f,
                0.5f,
                alpha(0xFFFFFFFF, 0.20f)
        );

        RenderUtil.Round.draw(ctx,
                topX + HEADER_W - 24.0f, topY + 4.0f,
                1.0f, HEADER_H - 8.0f,
                0.5f,
                alpha(0xFFFFFFFF, 0.20f)
        );

        String headerTitle = owner.trimToWidth(title, HEADER_W - 52.0f, 7);
        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                headerTitle,
                topX + 24.0f,
                topY + 12.4f,
                7,
                alpha(RenderUtil.ColorUtil.getTextColor(1, 1), 0.92f)
        );

        // mini-wave (чуть компактнее)
        float miniWaveX = topX + HEADER_W - 19.0f;
        float miniWaveBaseY = topY + HEADER_H - 5.0f;
        for (int i = 0; i < 4; i++) {
            float h = 2.0f + waveHeights[i] * 0.26f;
            RenderUtil.Round.draw(
                    ctx,
                    miniWaveX + i * 2.6f,
                    miniWaveBaseY - h,
                    1.7f,
                    h,
                    1.0f,
                    alpha(0xFFFFFFFF, 0.84f)
            );
        }

        if (openPc <= 0.01f) {
            controlsActive = false;
            return;
        }

        // Card
        RenderUtil.drawClientRect(ctx, cardX, cardY, CARD_W, CARD_H);

        float coverSize = 22.0f;
        float coverX = cardX + 4.0f;
        float coverY = cardY + 4.0f;

        Identifier art = mediaInfo != null ? mediaInfo.textureId() : null;
        if (art != null) {
            RenderUtil.Image.draw(ctx, art, coverX, coverY, coverSize, coverSize, 6.5f,
                    alpha(0xFFFFFFFF, 0.98f));
        } else {
            RenderUtil.Round.draw(ctx, coverX, coverY, coverSize, coverSize, 6.5f,
                    alpha(0xFF000000, 0.42f));
            RenderUtil.Image.draw(ctx, NOTE_ICON, coverX + 8.2f, coverY + 6.5f, 6.0f, 9.0f,
                    alpha(0xFFFFFFFF, 0.70f));
        }

        String fullArtist = artist.isBlank() ? "Unknown artist" : artist;

        // Текст компактнее
        String titleText = owner.trimToWidth(title, CARD_W - 86.0f, 7);
        String artistText = owner.trimToWidth(fullArtist, CARD_W - 86.0f, 6);

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, titleText,
                cardX + 30.0f, cardY + 11.0f, 7,
                alpha(RenderUtil.ColorUtil.getTextColor(1, 1), 0.95f));

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, artistText,
                cardX + 30.0f, cardY + 19.0f, 6,
                alpha(RenderUtil.ColorUtil.getTextColor(1, 1), 0.60f));

        // Controls (меньше и ближе)
        float iconSize = 6.5f;
        float controlsY = cardY + 11.0f;
        float rightPad = 6.0f;
        float step = 10.0f;

        nextButtonX = cardX + CARD_W - rightPad - iconSize;
        nextButtonY = controlsY;
        nextButtonW = iconSize;
        nextButtonH = iconSize;

        playButtonX = nextButtonX - step;
        playButtonY = controlsY;
        playButtonW = iconSize;
        playButtonH = iconSize;

        prevButtonX = playButtonX - step;
        prevButtonY = controlsY;
        prevButtonW = iconSize;
        prevButtonH = iconSize;

        controlsActive = true;

        drawControlIcon(ctx, PREV_ICON, prevButtonX, prevButtonY, prevButtonW, prevButtonH,
                PREV_ICON_TEX_SIZE, alpha(0xFFFFFFFF, 0.86f));

        drawControlIcon(ctx, (mediaInfo != null && mediaInfo.playing()) ? PAUSE_ICON : PLAY_ICON,
                playButtonX, playButtonY, playButtonW, playButtonH,
                CONTROL_ICON_TEX_SIZE, alpha(0xFFFFFFFF, 0.92f));

        drawControlIcon(ctx, SKIP_ICON, nextButtonX, nextButtonY, nextButtonW, nextButtonH,
                CONTROL_ICON_TEX_SIZE, alpha(0xFFFFFFFF, 0.86f));

        // Progress/time (компактнее)
        float progress = mediaInfo == null ? 0.0f : mediaInfo.progress();
        if (!Float.isFinite(progress)) progress = 0.0f;
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        long durationMs = mediaInfo == null ? 0L : mediaInfo.durationMs();
        long positionMs = mediaInfo == null ? 0L : mediaInfo.positionMs();

        if (durationMs <= 0L) {
            durationMs = 240000L;
            positionMs = (long) (durationMs * progress);
        } else {
            positionMs = Math.max(0L, Math.min(durationMs, positionMs));
        }

        String leftTime = formatTime(positionMs);
        String rightTime = formatTime(durationMs);

        int timeSize = 4; // было 5
        float timeY = cardY + CARD_H - 4.4f;

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, leftTime,
                cardX + 6.0f, timeY, timeSize,
                alpha(RenderUtil.ColorUtil.getTextColor(1, 1), 0.84f));

        float rightWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, rightTime, timeSize);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, rightTime,
                cardX + CARD_W - 6.0f - rightWidth, timeY, timeSize,
                alpha(RenderUtil.ColorUtil.getTextColor(1, 1), 0.84f));

        float progressX = cardX + 30.0f;
        float progressY = cardY + CARD_H - 6.0f;
        float progressW = CARD_W - 60.0f;

        RenderUtil.Round.draw(ctx, progressX, progressY, progressW, 2.0f, 1.0f,
                alpha(0xFFFFFFFF, 0.18f));
        if (progress > 0.001f) {
            RenderUtil.Round.draw(ctx, progressX, progressY, progressW * progress, 2.0f, 1.0f,
                    alpha(0xFFFFFFFF, 0.78f));
        }
    }

    private static String safe(String text) {
        if (text == null) return "";
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    public boolean handleClick(float mouseX, float mouseY) {
        if (inside(mouseX, mouseY, headerX, headerY, headerW, headerH)) {
            expanded = !expanded;
            return true;
        }

        if (!controlsActive || expandAnim < 0.2f) {
            return false;
        }

        if (inside(mouseX, mouseY, prevButtonX, prevButtonY, prevButtonW, prevButtonH)) {
            MediaUtil.skipPrevious();
            return true;
        }
        if (inside(mouseX, mouseY, playButtonX, playButtonY, playButtonW, playButtonH)) {
            MediaUtil.togglePlayPause();
            return true;
        }
        if (inside(mouseX, mouseY, nextButtonX, nextButtonY, nextButtonW, nextButtonH)) {
            MediaUtil.skipNext();
            return true;
        }

        return false;
    }

    private void updateMusicWave(int bars, boolean playing) {
        if (waveHeights == null || waveHeights.length != bars) {
            waveHeights = new float[bars];
            waveTargets = new float[bars];
            for (int i = 0; i < bars; i++) {
                waveHeights[i] = 4.0f;
                waveTargets[i] = 4.0f;
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastWaveUpdate > 150L) {
            lastWaveUpdate = now;
            for (int i = 0; i < bars; i++) {
                waveTargets[i] = playing ? (2.2f + (float) (Math.random() * 7.0f)) : 3.0f;
            }
        }

        for (int i = 0; i < bars; i++) {
            waveHeights[i] += (waveTargets[i] - waveHeights[i]) * 0.5f;
        }
    }

    private static String formatTime(long millis) {
        long totalSec = Math.max(0L, millis / 1000L);
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10L ? "0" : "") + sec;
    }

    private static int alpha(int color, float alphaPc) {
        int a = Math.max(0, Math.min(255, (int) (255.0f * Math.max(0.0f, Math.min(1.0f, alphaPc)))));
        return RenderUtil.ColorUtil.replAlpha(color, a);
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx <= x + w && my <= y + h;
    }

    private static float easeOut(float value) {
        float t = Math.max(0.0f, Math.min(1.0f, value));
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private void drawControlIcon(DrawContext ctx, Identifier icon, float x, float y, float w, float h,
                                 int textureSize, int color) {
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        int drawW = Math.max(1, Math.round(w));
        int drawH = Math.max(1, Math.round(h));

        int texSize = Math.max(1, textureSize);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon,
                drawX, drawY, 0.0f, 0.0f,
                drawW, drawH,
                texSize, texSize,
                texSize, texSize,
                color);
    }
}