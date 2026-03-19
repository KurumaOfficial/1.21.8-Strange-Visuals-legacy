package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD-элемент: кулдауны — показывает текущие перезарядки предметов.
 */
public class CooldownHud extends HudElement {

    /* ── Размеры ── */
    public static final float W = 92f;
    public static final float H = 20f;

    private static final float GAP = 4f;

    /* ── Inner classes ── */

    private static class CooldownInfo {
        private final ItemStack stack;
        private final int remainingTicks;
        private final int totalTicks;
        private final float fillProgress;

        private CooldownInfo(ItemStack stack, int remainingTicks, int totalTicks, float fillProgress) {
            this.stack = stack;
            this.remainingTicks = remainingTicks;
            this.totalTicks = totalTicks;
            this.fillProgress = fillProgress;
        }
    }

    private static class CooldownState {
        private final int remainingTicks;
        private final int totalTicks;

        private CooldownState(int remainingTicks, int totalTicks) {
            this.remainingTicks = remainingTicks;
            this.totalTicks = totalTicks;
        }
    }

    /* ── Reflection caches (CRIT-03) ── */
    private transient Field cachedCdMapField;
    private transient Field cachedCdTickField;
    private transient Class<?> lastCdManagerClass;
    private transient Method cachedGroupMethod;
    private transient Class<?> lastGroupManagerClass;
    private transient Method cachedIsCdMethod;
    private transient Class<?> lastIsCdManagerClass;
    private transient List<Field> cachedEntryFields;
    private transient Class<?> lastEntryClass;

    /* ── HudElement contract ── */

    @Override
    public void initPosition(int sw, int sh) {
        x = 12f;
        y = sh * 0.28f;
    }

    @Override
    public float getWidth() {
        return W;
    }

    @Override
    public float getHeight() {
        return Math.max(H, getGroupHeight());
    }

    @Override
    public void render(DrawContext ctx, boolean editing) {
        this.editing = editing;

        List<CooldownInfo> infos = getCooldownInfos();

        boolean preview = infos.isEmpty() && editing;
        int count = preview ? 2 : infos.size();

        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            float x = this.x;
            float y = this.y + i * (H + GAP);

            ItemStack icon;
            String timeText;
            float fill;

            if (preview) {
                if (i == 0) {
                    icon = Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack();
                    timeText = "128 сек";
                    fill = 0.12f;
                } else {
                    icon = Items.ENDER_PEARL.getDefaultStack();
                    timeText = "16 сек";
                    fill = 0.76f;
                }
            } else {
                CooldownInfo info = infos.get(i);
                icon = info.stack;
                timeText = formatCooldownTime(info.remainingTicks);
                fill = info.fillProgress;
            }

            drawCooldownCard(ctx, x, y, icon, timeText, fill);
        }
    }

    /* ── Card rendering ── */

    private void drawCooldownCard(DrawContext ctx, float x, float y, ItemStack icon, String timeText, float fillProgress) {
        RenderUtil.drawClientRect(ctx, x, y, W, H);

        float iconX = x + 5f;
        float iconY = y + 3f;

        if (icon != null && !icon.isEmpty()) {
            drawScaledItem(ctx, icon, iconX, iconY, 0.62f);
        }

        RenderUtil.Round.draw(
                ctx,
                x + 16f,
                y + 4f,
                1.2f,
                12f,
                0.6f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 45)
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                timeText,
                x + 22f,
                y + 8.3f,
                4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 230)
        );

        float barX = x + 22f;
        float barY = y + 14.2f;
        float barW = W - 28f;
        float barH = 3f;
        float radius = barH / 2f;

        RenderUtil.Round.draw(
                ctx,
                barX,
                barY,
                barW,
                barH,
                radius,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 60)
        );

        float fillW = barW * MathHelper.clamp(fillProgress, 0f, 1f);
        if (fillW > 0.01f) {
            fillW = Math.max(fillW, barH);

            RenderUtil.Round.draw(
                    ctx,
                    barX,
                    barY,
                    Math.min(fillW, barW),
                    barH,
                    radius,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 235)
            );
        }
    }

    /* ── Cooldown data collection ── */

    private List<CooldownInfo> getCooldownInfos() {
        List<CooldownInfo> result = new ArrayList<>();

        if (mc.player == null) return result;

        Object manager = mc.player.getItemCooldownManager();
        Map<Object, CooldownState> states = readCooldownStates(manager);

        if (states.isEmpty()) return result;

        Map<Object, ItemStack> matchedStacks = new LinkedHashMap<>();

        collectCooldownStack(matchedStacks, manager, states, mc.player.getMainHandStack());
        collectCooldownStack(matchedStacks, manager, states, mc.player.getOffHandStack());

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            collectCooldownStack(matchedStacks, manager, states, mc.player.getInventory().getStack(i));
        }

        for (Map.Entry<Object, ItemStack> entry : matchedStacks.entrySet()) {
            CooldownState state = states.get(entry.getKey());
            if (state == null) continue;
            if (state.remainingTicks <= 0 || state.totalTicks <= 0) continue;

            float remainingNorm = MathHelper.clamp(state.remainingTicks / (float) state.totalTicks, 0f, 1f);
            float fill = 1f - remainingNorm;

            result.add(new CooldownInfo(
                    entry.getValue(),
                    state.remainingTicks,
                    state.totalTicks,
                    fill
            ));
        }

        result.sort((a, b) -> Integer.compare(b.remainingTicks, a.remainingTicks));
        return result;
    }

    private void collectCooldownStack(Map<Object, ItemStack> out, Object manager, Map<Object, CooldownState> states, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        Object key = resolveCooldownKeyForStack(manager, stack, states);
        if (key == null) return;
        if (out.containsKey(key)) return;

        out.put(key, stack.copy());
    }

    private Object resolveCooldownKeyForStack(Object manager, ItemStack stack, Map<Object, CooldownState> states) {
        Item item = stack.getItem();

        for (Object key : states.keySet()) {
            if (key == item) {
                return key;
            }
        }

        Identifier itemId = Registries.ITEM.getId(item);
        for (Object key : states.keySet()) {
            if (key instanceof Identifier id && id.equals(itemId)) {
                return key;
            }

            if (String.valueOf(key).equals(String.valueOf(itemId))) {
                return key;
            }
        }

        Object group = getCooldownGroupForStack(manager, stack);
        if (group != null) {
            for (Object key : states.keySet()) {
                if (key.equals(group) || String.valueOf(key).equals(String.valueOf(group))) {
                    return key;
                }
            }
        }

        if (isCoolingDown(manager, stack) && states.size() == 1) {
            return states.keySet().iterator().next();
        }

        return null;
    }

    /* ── Reflection-based cooldown readers (CRIT-03 cached) ── */

    private Object getCooldownGroupForStack(Object manager, ItemStack stack) {
        try {
            Class<?> mClass = manager.getClass();
            if (cachedGroupMethod == null || lastGroupManagerClass != mClass) {
                lastGroupManagerClass = mClass;
                cachedGroupMethod = null;
                for (Method m : mClass.getMethods()) {
                    if (m.getName().toLowerCase().contains("group") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        cachedGroupMethod = m;
                        break;
                    }
                }
            }
            if (cachedGroupMethod == null) return null;

            Class<?> param = cachedGroupMethod.getParameterTypes()[0];
            if (param.isAssignableFrom(stack.getClass())) {
                return cachedGroupMethod.invoke(manager, stack);
            }
            if (param.isAssignableFrom(stack.getItem().getClass())) {
                return cachedGroupMethod.invoke(manager, stack.getItem());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean isCoolingDown(Object manager, ItemStack stack) {
        try {
            Class<?> mClass = manager.getClass();
            if (cachedIsCdMethod == null || lastIsCdManagerClass != mClass) {
                lastIsCdManagerClass = mClass;
                cachedIsCdMethod = null;
                for (Method m : mClass.getMethods()) {
                    if (m.getName().equals("isCoolingDown") && m.getParameterCount() == 1
                            && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                        m.setAccessible(true);
                        cachedIsCdMethod = m;
                        break;
                    }
                }
            }
            if (cachedIsCdMethod == null) return false;

            Class<?> param = cachedIsCdMethod.getParameterTypes()[0];
            if (param.isAssignableFrom(stack.getClass())) {
                Object r = cachedIsCdMethod.invoke(manager, stack);
                if (r instanceof Boolean b) return b;
            }
            if (param.isAssignableFrom(stack.getItem().getClass())) {
                Object r = cachedIsCdMethod.invoke(manager, stack.getItem());
                if (r instanceof Boolean b) return b;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private Map<Object, CooldownState> readCooldownStates(Object manager) {
        Map<Object, CooldownState> result = new LinkedHashMap<>();
        if (manager == null) return result;

        try {
            Class<?> mClass = manager.getClass();
            if (lastCdManagerClass != mClass) {
                lastCdManagerClass = mClass;
                cachedCdMapField = null;
                cachedCdTickField = null;
                for (Field field : mClass.getDeclaredFields()) {
                    if (cachedCdMapField == null && Map.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        cachedCdMapField = field;
                    } else if (cachedCdTickField == null && field.getType() == int.class) {
                        field.setAccessible(true);
                        cachedCdTickField = field;
                    }
                }
            }

            if (cachedCdMapField == null) return result;

            Object rawMap = cachedCdMapField.get(manager);
            if (!(rawMap instanceof Map<?, ?>)) return result;

            int currentTick = cachedCdTickField != null ? cachedCdTickField.getInt(manager) : 0;

            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawMap).entrySet()) {
                Object stateObj = entry.getValue();
                if (stateObj == null) continue;

                int[] ticks = readCooldownEntryTicks(stateObj);
                int startTick = ticks[0];
                int endTick = ticks[1];

                if (endTick <= startTick) continue;

                int remainingTicks = Math.max(0, endTick - currentTick);
                int totalTicks = Math.max(1, endTick - startTick);

                if (remainingTicks <= 0) continue;

                result.put(entry.getKey(), new CooldownState(remainingTicks, totalTicks));
            }
        } catch (Throwable ignored) {}

        return result;
    }

    private int[] readCooldownEntryTicks(Object stateObj) {
        try {
            Class<?> eClass = stateObj.getClass();
            if (lastEntryClass != eClass) {
                lastEntryClass = eClass;
                cachedEntryFields = new ArrayList<>();
                for (Field field : eClass.getDeclaredFields()) {
                    if (field.getType() == int.class) {
                        field.setAccessible(true);
                        cachedEntryFields.add(field);
                    }
                }
            }

            if (cachedEntryFields != null && cachedEntryFields.size() >= 2) {
                int a = cachedEntryFields.get(0).getInt(stateObj);
                int b = cachedEntryFields.get(1).getInt(stateObj);
                return a <= b ? new int[]{a, b} : new int[]{b, a};
            }
        } catch (Throwable ignored) {}

        return new int[]{0, 0};
    }

    /* ── Formatting / sizing helpers ── */

    private String formatCooldownTime(int remainingTicks) {
        int seconds = Math.max(1, (int) Math.ceil(remainingTicks / 20.0));
        return seconds + " сек";
    }

    private int getDisplayCount() {
        int count = getCooldownInfos().size();
        if (count <= 0 && editing) return 2;
        return count;
    }

    private float getGroupHeight() {
        int count = getDisplayCount();
        if (count <= 0) return 0f;
        return count * H + (count - 1) * GAP;
    }
}
