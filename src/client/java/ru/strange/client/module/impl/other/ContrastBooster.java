package ru.strange.client.module.impl.other;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Повышение контраста",
        description = "Делает цвета ярче и контрастнее",
        category = Category.Other,
        bind = -1
)
public class ContrastBooster extends Module {

    private static volatile ContrastBooster instance;

    private final SliderSetting contrast = new SliderSetting("Контраст", 1.2f, 1.0f, 2.0f, 0.05f, false);
    private final SliderSetting saturation = new SliderSetting("Насыщенность", 1.3f, 1.0f, 2.0f, 0.05f, false);
    private final SliderSetting gamma = new SliderSetting("Гамма", 1.1f, 0.8f, 1.5f, 0.05f, false);

    public ContrastBooster() {
        if (instance != null) {
            throw new IllegalStateException("ContrastBooster module already initialized");
        }
        instance = this;
        addSettings(contrast, saturation, gamma);
    }

    public static ContrastBooster getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.enable;
    }

    public static float getContrast() {
        return instance == null ? 1.0f : instance.contrast.get();
    }

    public static float getSaturation() {
        return instance == null ? 1.0f : instance.saturation.get();
    }

    public static float getGamma() {
        return instance == null ? 1.0f : instance.gamma.get();
    }
}
