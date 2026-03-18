package ru.strange.client.module.impl.other;

import java.lang.reflect.Method;

import net.minecraft.client.option.CloudRenderMode;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;

@IModule(
        name = "Оптимизация",
        description = "",
        category = Category.Other,
        bind = -1
)
public class Optimization extends Module {

    private final BooleanSetting disableParticles = new BooleanSetting("Партиклы", false);
    private final BooleanSetting disableClouds = new BooleanSetting("Облака", false);
    private final BooleanSetting reduceEntityRendering = new BooleanSetting("Энтити", false);
    private final BooleanSetting reduceViewDistance = new BooleanSetting("Дальность чанков", false);
    private final BooleanSetting reduceSimulationDistance = new BooleanSetting("Симуляция", false);
    private final BooleanSetting disableEntityShadows = new BooleanSetting("Тени энтити", false);
    private final BooleanSetting disableVsync = new BooleanSetting("VSync", false);

    private CloudRenderMode originalCloudMode;
    private double originalEntityDistance = 1.0;

    private Object originalViewDistance;
    private Object originalSimulationDistance;
    private Object originalEntityShadows;
    private Object originalVsync;
    private Object originalParticles;

    public Optimization() {
        addSettings(
                disableParticles,
                disableClouds,
                reduceEntityRendering,
                reduceViewDistance,
                reduceSimulationDistance,
                disableEntityShadows,
                disableVsync
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();

        if (mc.options != null) {
            try {
                originalCloudMode = mc.options.getCloudRenderMode().getValue();
            } catch (Throwable ignored) {
            }

            try {
                originalEntityDistance = mc.options.getEntityDistanceScaling().getValue();
            } catch (Throwable ignored) {
            }

            originalViewDistance = tryGetSimpleOptionValue("getViewDistance");
            originalSimulationDistance = tryGetSimpleOptionValue("getSimulationDistance");
            originalEntityShadows = tryGetSimpleOptionValue("getEntityShadows");
            originalVsync = tryGetSimpleOptionValue("getEnableVsync");

            originalParticles = tryGetParticleOption();
        }

        applyOptimizations();
    }

    @Override
    public void onDisable() {
        restoreSettings();
        super.onDisable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.options == null) return;
        applyOptimizations();
    }

    private void applyOptimizations() {
        if (mc.options == null) return;

        if (disableClouds.get()) {
            try {
                mc.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
            } catch (Throwable ignored) {
            }
        }

        if (reduceEntityRendering.get()) {
            try {
                mc.options.getEntityDistanceScaling().setValue(0.5);
            } catch (Throwable ignored) {
            }
        }

        if (disableParticles.get()) {
            trySetParticleOption("MINIMAL");
        }

        if (reduceViewDistance.get()) {
            trySetSimpleOptionNumeric("getViewDistance", 6);
        }

        if (reduceSimulationDistance.get()) {
            trySetSimpleOptionNumeric("getSimulationDistance", 6);
        }

        if (disableEntityShadows.get()) {
            trySetSimpleOptionValue("getEntityShadows", Boolean.FALSE);
        }

        if (disableVsync.get()) {
            trySetSimpleOptionValue("getEnableVsync", Boolean.FALSE);
        }
    }

    private void restoreSettings() {
        if (mc.options == null) return;

        try {
            if (originalCloudMode != null) {
                mc.options.getCloudRenderMode().setValue(originalCloudMode);
            }
        } catch (Throwable ignored) {
        }

        try {
            mc.options.getEntityDistanceScaling().setValue(originalEntityDistance);
        } catch (Throwable ignored) {
        }

        trySetSimpleOptionValue("getViewDistance", originalViewDistance);
        trySetSimpleOptionValue("getSimulationDistance", originalSimulationDistance);
        trySetSimpleOptionValue("getEntityShadows", originalEntityShadows);
        trySetSimpleOptionValue("getEnableVsync", originalVsync);

        if (originalParticles != null) {
            restoreParticleOption(originalParticles);
        }
    }

    public boolean shouldDisableParticles() {
        return enable && disableParticles.get();
    }

    private Object tryGetSimpleOptionValue(String getter) {
        if (mc.options == null) return null;

        try {
            Method method = mc.options.getClass().getMethod(getter);
            Object option = method.invoke(mc.options);
            if (option == null) return null;

            try {
                Method getValue = option.getClass().getMethod("getValue");
                return getValue.invoke(option);
            } catch (Throwable ignored) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void trySetSimpleOptionNumeric(String getter, int desired) {
        if (mc.options == null) return;

        try {
            Method method = mc.options.getClass().getMethod(getter);
            Object option = method.invoke(mc.options);
            if (option == null) return;

            int value = desired;

            try {
                Method minMethod = option.getClass().getMethod("getMin");
                Method maxMethod = option.getClass().getMethod("getMax");

                Object minObj = minMethod.invoke(option);
                Object maxObj = maxMethod.invoke(option);

                if (minObj instanceof Number min) value = Math.max(value, min.intValue());
                if (maxObj instanceof Number max) value = Math.min(value, max.intValue());
            } catch (Throwable ignored) {
            }

            try {
                Method setValue = option.getClass().getMethod("setValue", Object.class);
                setValue.invoke(option, Integer.valueOf(value));
                return;
            } catch (Throwable ignored) {
            }

            try {
                Method setValue = option.getClass().getMethod("setValue", int.class);
                setValue.invoke(option, value);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private void trySetSimpleOptionValue(String getter, Object value) {
        if (mc.options == null || value == null) return;

        try {
            Method method = mc.options.getClass().getMethod(getter);
            Object option = method.invoke(mc.options);
            if (option == null) return;

            try {
                Method setValue = option.getClass().getMethod("setValue", Object.class);
                setValue.invoke(option, value);
                return;
            } catch (Throwable ignored) {
            }

            try {
                Method setValue = option.getClass().getMethod("setValue", value.getClass());
                setValue.invoke(option, value);
                return;
            } catch (Throwable ignored) {
            }

            if (value instanceof Boolean b) {
                try {
                    Method setValue = option.getClass().getMethod("setValue", boolean.class);
                    setValue.invoke(option, b.booleanValue());
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Object tryGetParticleOption() {
        if (mc.options == null) return null;

        String[] possibleGetters = {
                "getParticles",
                "getParticleStatus",
                "getParticlesMode"
        };

        for (String getter : possibleGetters) {
            try {
                Method method = mc.options.getClass().getMethod(getter);
                Object option = method.invoke(mc.options);
                if (option == null) continue;

                Method getValue = option.getClass().getMethod("getValue");
                Object value = getValue.invoke(option);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private void trySetParticleOption(String enumName) {
        if (mc.options == null || enumName == null) return;

        String[] possibleGetters = {
                "getParticles",
                "getParticleStatus",
                "getParticlesMode"
        };

        for (String getter : possibleGetters) {
            try {
                Method method = mc.options.getClass().getMethod(getter);
                Object option = method.invoke(mc.options);
                if (option == null) continue;

                Method getValue = option.getClass().getMethod("getValue");
                Object current = getValue.invoke(option);
                if (current == null || !current.getClass().isEnum()) continue;

                @SuppressWarnings({"rawtypes", "unchecked"})
                Object enumValue = Enum.valueOf((Class<? extends Enum>) current.getClass(), enumName);

                trySetSimpleOptionValue(getter, enumValue);
            } catch (Throwable ignored) {
            }
        }
    }

    private void restoreParticleOption(Object originalValue) {
        if (mc.options == null || originalValue == null) return;

        String[] possibleGetters = {
                "getParticles",
                "getParticleStatus",
                "getParticlesMode"
        };

        for (String getter : possibleGetters) {
            trySetSimpleOptionValue(getter, originalValue);
        }
    }
}