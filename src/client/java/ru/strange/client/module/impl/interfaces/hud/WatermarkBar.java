package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.module.api.setting.impl.MultiBooleanSetting;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD-элемент: ватермарка (полоска Strange Visual + сегменты).
 */
public class WatermarkBar extends HudElement {

    private static final float HEIGHT = 16f;
    private static final float WIDTH_SPEED = 0.18f;
    private static final float SEG_SPEED = 0.22f;

    private final Identifier logoIcon = Identifier.of(Strange.rootRes, "/icons/gui/logo.png");

    private float animatedWidth = 71f;
    private final Map<String, Float> segProgress = new LinkedHashMap<>();

    private final MultiBooleanSetting settings;

    public WatermarkBar(MultiBooleanSetting settings) {
        this.settings = settings;
    }

    @Override
    public void initPosition(int sw, int sh) {
        x = 10f;
        y = 10f;
    }

    @Override
    public float getWidth() {
        return animatedWidth - 2f;
    }

    @Override
    public float getHeight() {
        return HEIGHT;
    }

    @Override
    public void render(DrawContext ctx, boolean editing) {
        if (mc.player == null || mc.world == null) return;

        float dt = 1.0f;

        String nickText = mc.player.getName().getString();

        String fpsText = mc.getCurrentFps() + " FPS";

        int pingValue = -1;
        if (mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
            pingValue = mc.getNetworkHandler()
                    .getPlayerListEntry(mc.player.getUuid())
                    .getLatency();
        }
        String pingText = pingValue >= 0 ? pingValue + " ms" : "N/A";

        String timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String serverText = getServerName();

        Map<String, String> segments = new LinkedHashMap<>();
        segments.put("Ник", nickText);
        segments.put("ФПС", fpsText);
        segments.put("Пинг", pingText);
        segments.put("Время", timeText);
        segments.put("Сервер", serverText);

        for (Map.Entry<String, String> seg : segments.entrySet()) {
            String key = seg.getKey();
            boolean enabled = settings.get(key);

            float p = segProgress.getOrDefault(key, 0f);
            float target = enabled ? 1f : 0f;

            p = approach(p, target, SEG_SPEED * dt);
            segProgress.put(key, p);
        }

        String title = "Strange Visual";
        float titleWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, title, 7);
        float baseWidth = 15f + titleWidth + 6f;

        float targetWidth = baseWidth;

        for (Map.Entry<String, String> seg : segments.entrySet()) {
            String text = seg.getValue();

            float p = segProgress.getOrDefault(seg.getKey(), 0f);
            if (p <= 0.001f) continue;

            float w = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 7);
            targetWidth += (w + 6f) * p;
        }

        animatedWidth = lerp(animatedWidth, targetWidth, WIDTH_SPEED * dt);

        RenderUtil.drawClientRect(ctx, x, y, animatedWidth - 2f, HEIGHT);

        RenderUtil.Image.draw(
                ctx,
                logoIcon,
                x + 4.09f, y + 4f,
                8.17f, 8f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 178)
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                title,
                x + 15f,
                y + 10.5f,
                7,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 178)
        );

        float cursorX = x + baseWidth;

        for (Map.Entry<String, String> seg : segments.entrySet()) {
            String text = seg.getValue();

            float p = segProgress.getOrDefault(seg.getKey(), 0f);
            if (p <= 0.01f) continue;

            float textW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 7);
            cursorX = drawSegmentAnimated(ctx, cursorX, y, text, p, textW, 6f);
        }
    }

    private float drawSegmentAnimated(DrawContext ctx, float cursorX, float y, String text, float p, float textW, float pad) {
        int baseAlphaText = 178;
        int baseAlphaSep = 150;

        int aText = (int) (baseAlphaText * p);
        int aSep = (int) (baseAlphaSep * p);

        float slide = (1f - p) * 6f;
        float drawX = cursorX - slide;

        RenderUtil.Round.draw(
                ctx,
                drawX - 3f,
                y + 4f,
                1f,
                8f,
                0.5f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), aSep)
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                text,
                drawX,
                y + 10.5f,
                7,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), aText)
        );

        return cursorX + (textW + pad) * p;
    }

    private String getServerName() {
        if (mc.isInSingleplayer()) return "singleplayer";

        if (mc.getCurrentServerEntry() != null) {
            if (mc.getCurrentServerEntry().name != null && !mc.getCurrentServerEntry().name.isEmpty()) {
                return mc.getCurrentServerEntry().name;
            }

            if (mc.getCurrentServerEntry().address != null && !mc.getCurrentServerEntry().address.isEmpty()) {
                return mc.getCurrentServerEntry().address;
            }
        }

        return "unknown";
    }
}
