package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class BossBarHudRenderer {

    public static final float W = 178.0f;
    public static final float H = 28.0f;

    private final WaterMark owner;

    public BossBarHudRenderer(WaterMark owner) {
        this.owner = owner;
    }

    public void render(DrawContext ctx, float x, float y) {
        BossSnapshot snapshot = resolveBossSnapshot();
        if (snapshot == null) {
            if (!owner.isEditing()) {
                return;
            }
            snapshot = new BossSnapshot("Boss Bar", 0.68f);
        }

        float progress = Math.max(0.0f, Math.min(1.0f, snapshot.progress));
        int titleColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 225);
        int secondaryColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 165);

        RenderUtil.drawClientRect(ctx, x, y, W, H);

        String title = owner.trimToWidth(snapshot.name, W - 62.0f, 6);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, title, x + 8.0f, y + 9.0f, 6, titleColor);

        int percent = Math.round(progress * 100.0f);
        String percentText = percent + "%";
        float percentWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, percentText, 6);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, percentText, x + W - 8.0f - percentWidth, y + 9.0f, 6, secondaryColor);

        float barX = x + 8.0f;
        float barY = y + 18.0f;
        float barW = W - 16.0f;
        float barH = 6.0f;

        RenderUtil.Round.draw(ctx, barX, barY, barW, barH, 2.0f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 210));

        if (progress > 0.0f) {
            RenderUtil.Round.draw(ctx, barX, barY, barW * progress, barH, 2.0f,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 225));
        }
    }

    public boolean hasBoss() {
        return resolveBossSnapshot() != null;
    }

    private BossSnapshot resolveBossSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return null;
        }

        try {
            Object bossBarHud = resolveBossBarHud(client.inGameHud);
            if (bossBarHud == null) {
                return null;
            }

            Map<?, ?> bars = extractBossBars(bossBarHud);
            if (bars == null || bars.isEmpty()) {
                return null;
            }

            Object firstBoss = bars.values().iterator().next();
            if (firstBoss == null) {
                return null;
            }

            String name = resolveBossName(firstBoss);
            float progress = resolveBossProgress(firstBoss);

            if (!Float.isFinite(progress)) {
                progress = 0.0f;
            }

            if (name == null || name.isBlank()) {
                name = "Boss";
            }

            return new BossSnapshot(name, Math.max(0.0f, Math.min(1.0f, progress)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object resolveBossBarHud(Object inGameHud) {
        Object bossBarHud = invokeMethod(inGameHud, "getBossBarHud");
        if (bossBarHud != null) {
            return bossBarHud;
        }

        try {
            for (Field field : inGameHud.getClass().getDeclaredFields()) {
                if (field.getType().getSimpleName().contains("BossBarHud")) {
                    field.setAccessible(true);
                    Object value = field.get(inGameHud);
                    if (value != null) {
                        return value;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> extractBossBars(Object bossBarHud) {
        try {
            Field field = bossMapField;
            if (field == null || field.getDeclaringClass() != bossBarHud.getClass()) {
                for (Field candidate : bossBarHud.getClass().getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(candidate.getType())) {
                        candidate.setAccessible(true);
                        field = candidate;
                        bossMapField = candidate;
                        break;
                    }
                }
            }

            if (field == null) {
                return null;
            }

            Object value = field.get(bossBarHud);
            return value instanceof Map<?, ?> map ? map : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String resolveBossName(Object bossBar) {
        Object nameObj = invokeMethod(bossBar, "getName");
        if (nameObj instanceof Text text) {
            return text.getString();
        }
        return nameObj == null ? "" : nameObj.toString();
    }

    private float resolveBossProgress(Object bossBar) {
        Object value = invokeMethod(bossBar, "getPercent");
        if (!(value instanceof Number)) {
            value = invokeMethod(bossBar, "getProgress");
        }

        if (value instanceof Number number) {
            return number.floatValue();
        }
        return 0.0f;
    }

    private Object invokeMethod(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static volatile Field bossMapField;

    private record BossSnapshot(String name, float progress) {
    }
}
