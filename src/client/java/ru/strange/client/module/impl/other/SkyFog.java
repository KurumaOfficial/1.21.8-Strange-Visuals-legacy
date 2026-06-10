package ru.strange.client.module.impl.other;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

import java.awt.*;

@IModule(
        name = "Туман в небе",
        description = "Добавляет разноцветный туман в небе",
        category = Category.Other,
        bind = -1
)
public class SkyFog extends Module {

    private static volatile SkyFog instance;

    private final BooleanSetting enabled = new BooleanSetting("Включено", true);
    private final HueSetting fogColor = new HueSetting("Цвет тумана", new Color(135, 206, 235));
    private final SliderSetting density = new SliderSetting("Плотность", 0.5f, 0.1f, 1.0f, 0.05f, false);
    private final SliderSetting height = new SliderSetting("Высота", 128.0f, 64.0f, 256.0f, 8.0f, false);

    public SkyFog() {
        if (instance != null) {
            throw new IllegalStateException("SkyFog module already initialized");
        }
        instance = this;
        addSettings(enabled, fogColor, density, height);
    }

    public static SkyFog getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.enable && instance.enabled.get();
    }

    public static int getFogColorRGB() {
        return instance == null ? new Color(135, 206, 235).getRGB() : instance.fogColor.getRGB();
    }

    public static float getDensity() {
        return instance == null ? 0.5f : instance.density.get();
    }

    public static float getHeight() {
        return instance == null ? 128.0f : instance.height.get();
    }
}
