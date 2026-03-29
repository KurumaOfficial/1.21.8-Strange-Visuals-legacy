package ru.strange.client.module.impl.interfaces;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventScreen;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.renderengine.builders.Builder;
import ru.strange.client.renderengine.builders.states.QuadColorState;
import ru.strange.client.renderengine.builders.states.QuadRadiusState;
import ru.strange.client.renderengine.builders.states.SizeState;
import ru.strange.client.utils.combat.CombatUtil;
import ru.strange.client.utils.combat.CombatStateTracker;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;

@IModule(
        name = "Custom Crosshair",
        description = "Кастомный прицел",
        category = Category.Interface,
        bind = -1
)
public class CustomCrosshair extends Module {
    private static final float DIAGONAL_UNIT = 0.70710677f;
    private static final float[] HIT_MARKER_DX = {DIAGONAL_UNIT, -DIAGONAL_UNIT, -DIAGONAL_UNIT, DIAGONAL_UNIT};
    private static final float[] HIT_MARKER_DY = {DIAGONAL_UNIT, DIAGONAL_UNIT, -DIAGONAL_UNIT, -DIAGONAL_UNIT};

    private static CustomCrosshair instance;

    /* ═══════ Настройки ═══════ */

    private final ModeSetting mode = new ModeSetting("Режим", "Крест",
            "Крест", "Точка", "Круг", "Скобки");

    // ─── Крест ───
    private final SliderSetting crossLength = new SliderSetting("Длина линий",
            6.0f, 2.0f, 16.0f, 0.5f, false)
            .hidden(() -> !mode.is("Крест"));

    private final SliderSetting crossThickness = new SliderSetting("Толщина линий",
            2.0f, 1.5f, 5.0f, 0.25f, false)
            .hidden(() -> !mode.is("Крест"));

    private final SliderSetting crossGap = new SliderSetting("Отступ от центра",
            3.0f, 0.0f, 12.0f, 0.5f, false)
            .hidden(() -> !mode.is("Крест"));

    private final BooleanSetting crossDot = new BooleanSetting("Точка в центре",
            false)
            .hidden(() -> !mode.is("Крест"));

    private final BooleanSetting crossDynamic = new BooleanSetting("Динамика",
            true)
            .hidden(() -> !mode.is("Крест"));

    // ─── Точка ───
    private final SliderSetting dotSize = new SliderSetting("Размер точки",
            4.0f, 3.0f, 12.0f, 0.5f, false)
            .hidden(() -> !mode.is("Точка"));

    // ─── Круг ───
    private final SliderSetting circleRadius = new SliderSetting("Радиус",
            8.0f, 4.0f, 20.0f, 0.5f, false)
            .hidden(() -> !mode.is("Круг"));

    private final SliderSetting circleThickness = new SliderSetting("Толщина кольца",
            1.5f, 0.5f, 4.0f, 0.25f, false)
            .hidden(() -> !mode.is("Круг"));

    private final BooleanSetting circleDot = new BooleanSetting("Точка в центре ",
            false)
            .hidden(() -> !mode.is("Круг"));

    private final BooleanSetting circleDynamic = new BooleanSetting("Динамика ",
            true)
            .hidden(() -> !mode.is("Круг"));

    // ─── Скобки ───
    private final SliderSetting bracketArm = new SliderSetting("Длина скобок",
            6.0f, 3.0f, 14.0f, 0.5f, false)
            .hidden(() -> !mode.is("Скобки"));

    private final SliderSetting bracketThickness = new SliderSetting("Толщина скобок",
            2.0f, 1.5f, 5.0f, 0.25f, false)
            .hidden(() -> !mode.is("Скобки"));

    private final SliderSetting bracketGap = new SliderSetting("Отступ скобок",
            4.0f, 1.0f, 14.0f, 0.5f, false)
            .hidden(() -> !mode.is("Скобки"));

    private final BooleanSetting bracketDot = new BooleanSetting("Точка в центре  ",
            false)
            .hidden(() -> !mode.is("Скобки"));

    private final BooleanSetting bracketDynamic = new BooleanSetting("Динамика  ",
            true)
            .hidden(() -> !mode.is("Скобки"));

    // ─── Общие ───
    private final SliderSetting opacity = new SliderSetting("Прозрачность",
            245, 60, 255, 5, false);

    private final BooleanSetting outlineOn = new BooleanSetting("Обводка", true);

    private final BooleanSetting targetOn = new BooleanSetting("Подсветка цели", true);

    private final BooleanSetting hitOn = new BooleanSetting("Хит маркер", true);

    private final HueSetting mainHue = new HueSetting("Цвет",
            new Color(255, 255, 255));

    private final HueSetting targetHue = new HueSetting("Цвет цели",
            new Color(100, 220, 255))
            .hidden(() -> !targetOn.get());

    private final HueSetting hitHue = new HueSetting("Цвет хита",
            new Color(255, 70, 70))
            .hidden(() -> !hitOn.get());

    /* ═══════ Анимации ═══════ */

    private float aGap, aTarget, aCrit, aHit;
    private long lastMs;

    private final HueSetting critReadyHue = new HueSetting("Цвет крита",
            new Color(255, 210, 95))
            .hidden(() -> !targetOn.get());

    /* ═══════ Инит ═══════ */

    public CustomCrosshair() {
        instance = this;
        addSettings(
                mode,
                crossLength, crossThickness, crossGap, crossDot, crossDynamic,
                dotSize,
                circleRadius, circleThickness, circleDot, circleDynamic,
                bracketArm, bracketThickness, bracketGap, bracketDot, bracketDynamic,
                opacity, outlineOn,
                targetOn, hitOn,
                mainHue, targetHue, critReadyHue, hitHue
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        aGap = 0;
        aTarget = 0;
        aCrit = 0;
        aHit = 0;
        lastMs = System.currentTimeMillis();
    }

    /* ═══════════════════════════════════════════
     *   Главный рендер
     * ═══════════════════════════════════════════ */

    @EventInit
    public void onScreen(EventScreen event) {
        if (!enable || mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) return;

        DrawContext ctx = event.drawContext();
        float cx = ctx.getScaledWindowWidth() / 2.0f;
        float cy = ctx.getScaledWindowHeight() / 2.0f;

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastMs) / 1000.0f, 0.05f);
        lastMs = now;

        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        boolean looking = isTargeting();
        boolean critReady = canDealCriticalToTarget(cooldown);
        float hitPulse = getHitPulse();

        boolean useDynamic = switch (mode.get()) {
            case "Крест" -> crossDynamic.get();
            case "Круг" -> circleDynamic.get();
            case "Скобки" -> bracketDynamic.get();
            default -> false;
        };

        float dynamicOffset = useDynamic ? (1.0f - cooldown) * 4.0f : 0;
        aGap = smooth(aGap, dynamicOffset, dt, 14.0f);
        aTarget = smooth(aTarget, (targetOn.get() && looking) ? 1.0f : 0.0f, dt, 10.0f);
        aCrit = smooth(aCrit, (targetOn.get() && critReady) ? 1.0f : 0.0f, dt, 11.0f);
        aHit = smooth(aHit, (hitOn.get() && hitPulse > 0.01f) ? hitPulse : 0.0f, dt, 16.0f);

        // ─── Цвет ───
        int alpha = cl((int) opacity.get(), 0, 255);
        Color bc = mainHue.getColor();
        Color color = new Color(bc.getRed(), bc.getGreen(), bc.getBlue(), alpha);

        if (aTarget > 0.005f) {
            Color tc = targetHue.getColor();
            color = lerpCol(color,
                    new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), alpha),
                    aTarget * 0.85f);
        }
        if (aCrit > 0.005f) {
            Color cc = critReadyHue.getColor();
            color = lerpCol(color,
                    new Color(cc.getRed(), cc.getGreen(), cc.getBlue(), alpha),
                    aCrit * 0.9f);
        }
        if (aHit > 0.005f) {
            Color hc = hitHue.getColor();
            color = lerpCol(color,
                    new Color(hc.getRed(), hc.getGreen(), hc.getBlue(), alpha),
                    aHit * 0.8f);
        }

        Color outColor = new Color(0, 0, 0, cl((int) (alpha * 0.55f), 0, 255));

        // ─── Рисуем ───
        switch (mode.get()) {
            case "Крест" -> {
                float gp = crossGap.get() + aGap;
                renderCross(ctx, cx, cy, crossLength.get(), crossThickness.get(),
                        gp, color, outColor);
                if (crossDot.get()) {
                    drawCircle(ctx, cx, cy, Math.max(1.5f, crossThickness.get() * 0.45f),
                            color, outColor);
                }
            }
            case "Точка" -> {
                drawCircle(ctx, cx, cy, dotSize.get() / 2.0f, color, outColor);
            }
            case "Круг" -> {
                float r = circleRadius.get() + aGap;
                renderRing(ctx, cx, cy, r, circleThickness.get(), color, outColor);
                if (circleDot.get()) {
                    drawCircle(ctx, cx, cy, Math.max(1.5f, circleThickness.get() * 0.5f),
                            color, outColor);
                }
            }
            case "Скобки" -> {
                float gp = bracketGap.get() + aGap;
                renderBrackets(ctx, cx, cy, bracketArm.get(), bracketThickness.get(),
                        gp, color, outColor);
                if (bracketDot.get()) {
                    drawCircle(ctx, cx, cy, Math.max(1.5f, bracketThickness.get() * 0.45f),
                            color, outColor);
                }
            }
        }

        // Хит маркер
        if (hitOn.get() && aHit > 0.01f) {
            float hmTh = switch (mode.get()) {
                case "Крест" -> crossThickness.get();
                case "Скобки" -> bracketThickness.get();
                case "Круг" -> circleThickness.get();
                default -> 2.0f;
            };
            renderHitMarker(ctx, cx, cy, aHit, alpha, hmTh);
        }
    }

    /* ═══════════════════════════════════════════
     *   КРЕСТ
     * ═══════════════════════════════════════════ */

    private void renderCross(DrawContext ctx, float cx, float cy,
                             float length, float th, float gp,
                             Color color, Color outColor) {
        float h = th / 2.0f;
        drawRectSegment(ctx, cx - h, cy - gp - length, th, length, color, outColor);
        drawRectSegment(ctx, cx - h, cy + gp, th, length, color, outColor);
        drawRectSegment(ctx, cx - gp - length, cy - h, length, th, color, outColor);
        drawRectSegment(ctx, cx + gp, cy - h, length, th, color, outColor);
    }

    /* ═══════════════════════════════════════════
     *   КРУГ — кольцо через BorderBuilder
     *   с правильным smoothness для ровных краёв
     * ═══════════════════════════════════════════ */

    private void renderRing(DrawContext ctx, float cx, float cy,
                            float radius, float ringThickness,
                            Color color, Color outColor) {
        float r = Math.max(4.0f, radius);
        float ringTh = Math.max(0.5f, Math.min(ringThickness, r * 0.35f));
        float d = r * 2.0f;

        // Фиксированный smoothness для идеально круглой формы
        // Используем высокое значение для плавных краёв как внешних, так и внутренних
        float sm = 1.0f;

        // Обводка
        if (outlineOn.get()) {
            float oR = r + 1.0f;
            float oD = oR * 2.0f;

            Builder.border()
                    .size(new SizeState(oD, oD))
                    .radius(new QuadRadiusState(oR))
                    .color(new QuadColorState(outColor))
                    .thickness(ringTh + 2.0f)
                    .smoothness(1.0f, 1.0f)
                    .build()
                    .render(cx - oR, cy - oR, ctx);
        }

        // Основное кольцо
        Builder.border()
                .size(new SizeState(d, d))
                .radius(new QuadRadiusState(r))
                .color(new QuadColorState(color))
                .thickness(ringTh)
                .smoothness(sm, sm)
                .build()
                .render(cx - r, cy - r, ctx);
    }

    /* ═══════════════════════════════════════════
     *   СКОБКИ
     * ═══════════════════════════════════════════ */

    private void renderBrackets(DrawContext ctx, float cx, float cy,
                                float armLen, float th, float gp,
                                Color color, Color outColor) {
        float arm = Math.max(3.0f, armLen * 0.6f);
        float off = gp + arm;
        drawRectSegment(ctx, cx - off, cy - off, arm, th, color, outColor);
        drawRectSegment(ctx, cx - off, cy - off, th, arm, color, outColor);
        drawRectSegment(ctx, cx + off - arm, cy - off, arm, th, color, outColor);
        drawRectSegment(ctx, cx + off - th, cy - off, th, arm, color, outColor);
        drawRectSegment(ctx, cx - off, cy + off - th, arm, th, color, outColor);
        drawRectSegment(ctx, cx - off, cy + off - arm, th, arm, color, outColor);
        drawRectSegment(ctx, cx + off - arm, cy + off - th, arm, th, color, outColor);
        drawRectSegment(ctx, cx + off - th, cy + off - arm, th, arm, color, outColor);
    }

    /* ═══════════════════════════════════════════
     *   ХИТ МАРКЕР
     * ═══════════════════════════════════════════ */

    private void renderHitMarker(DrawContext ctx, float cx, float cy,
                                 float factor, int baseAlpha, float baseTh) {
        float len = 5.0f * factor;
        float innerGap = 3.0f;
        float dotR = Math.max(1.0f, baseTh * 0.4f);
        float dotD = dotR * 2.0f;

        int alpha = cl((int) (baseAlpha * factor * 0.9f), 0, 255);
        Color hc = hitHue.getColor();
        Color hmColor = new Color(hc.getRed(), hc.getGreen(), hc.getBlue(), alpha);
        Color hmOut = new Color(0, 0, 0, cl((int) (alpha * 0.4f), 0, 255));

        for (int i = 0; i < 4; i++) {
            float cos = HIT_MARKER_DX[i];
            float sin = HIT_MARKER_DY[i];

            float x1 = cx + cos * innerGap;
            float y1 = cy + sin * innerGap;
            float x2 = cx + cos * (innerGap + len);
            float y2 = cy + sin * (innerGap + len);

            int steps = Math.max(6, (int) (len / 0.4f));

            if (outlineOn.get()) {
                float oR = dotR + 1.0f;
                float oD = oR * 2.0f;
                for (int s = 0; s <= steps; s++) {
                    float t = s / (float) steps;
                    float px = x1 + (x2 - x1) * t;
                    float py = y1 + (y2 - y1) * t;
                    drawRound(ctx, px - oR, py - oR, oD, oD, oR, hmOut);
                }
            }

            for (int s = 0; s <= steps; s++) {
                float t = s / (float) steps;
                float px = x1 + (x2 - x1) * t;
                float py = y1 + (y2 - y1) * t;
                drawRound(ctx, px - dotR, py - dotR, dotD, dotD, dotR, hmColor);
            }
        }
    }

    /* ═══════════════════════════════════════════
     *   Заполненный круг (точка)
     *   RectangleBuilder с smoothness для
     *   идеально круглых краёв при любом размере
     * ═══════════════════════════════════════════ */

    private void drawCircle(DrawContext ctx, float cx, float cy,
                            float radius, Color color, Color outColor) {
        float r = Math.max(1.5f, radius);
        float d = r * 2.0f;

        // smoothness: маленький круг → маленький smooth
        float sm = Math.max(0.2f, Math.min(1.0f, r / 8.0f));

        if (outlineOn.get()) {
            float oR = r + 1.0f;
            float oD = oR * 2.0f;
            float oSm = Math.max(0.2f, Math.min(1.0f, oR / 8.0f));

            Builder.rectangle()
                    .size(new SizeState(oD, oD))
                    .radius(new QuadRadiusState(oR))
                    .color(new QuadColorState(outColor))
                    .smoothness(oSm)
                    .build()
                    .render(cx - oR, cy - oR, ctx);
        }

        Builder.rectangle()
                .size(new SizeState(d, d))
                .radius(new QuadRadiusState(r))
                .color(new QuadColorState(color))
                .smoothness(sm)
                .build()
                .render(cx - r, cy - r, ctx);
    }

    /**
     * Round.draw обёртка с адаптивным smoothness
     * для хит-маркера и мелких элементов
     */
    private void drawRound(DrawContext ctx, float x, float y,
                           float w, float h, float radius, Color color) {
        float sm = Math.max(0.2f, Math.min(1.0f, radius / 6.0f));

        Builder.rectangle()
                .size(new SizeState(w, h))
                .radius(new QuadRadiusState(radius))
                .color(new QuadColorState(color))
                .smoothness(sm)
                .build()
                .render(x, y, ctx);
    }

    private void drawRectSegment(DrawContext ctx, float x, float y, float width, float height, Color color, Color outColor) {
        if (outlineOn.get()) {
            RenderUtil.Rect.draw(ctx, x - 1.0f, y - 1.0f, width + 2.0f, height + 2.0f, outColor);
        }
        RenderUtil.Rect.draw(ctx, x, y, width, height, color);
    }

    /* ═══════════════════════════════════════════
     *   Цвета
     * ═══════════════════════════════════════════ */

    private Color lerpCol(Color a, Color b, float t) {
        if (t <= 0.0f) return a;
        if (t >= 1.0f) return b;
        return new Color(
                cl(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), 0, 255),
                cl(Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t), 0, 255),
                cl(Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), 0, 255),
                cl(Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t), 0, 255)
        );
    }

    /* ═══════════════════════════════════════════
     *   Утилиты
     * ═══════════════════════════════════════════ */

    private boolean isTargeting() {
        if (mc.crosshairTarget instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            return e instanceof LivingEntity le && le.isAlive() && le != mc.player;
        }
        return false;
    }

    private boolean canDealCriticalToTarget(float cooldown) {
        if (mc.player == null || !(mc.crosshairTarget instanceof EntityHitResult ehr)) {
            return false;
        }

        Entity entity = ehr.getEntity();
        return CombatUtil.isValidAttackTarget(entity)
                && CombatUtil.isCriticalAttack(mc.player, entity, cooldown);
    }

    private float getHitPulse() {
        return CombatStateTracker.getInstance()
                .getGlobalPulse(CombatStateTracker.Marker.HIT, 220L);
    }

    private float smooth(float cur, float tgt, float dt, float speed) {
        float diff = tgt - cur;
        if (Math.abs(diff) < 0.001f) return tgt;
        return cur + diff * Math.min(dt * speed, 1.0f);
    }

    private int cl(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static boolean isActive() {
        return instance != null && instance.enable;
    }
}
