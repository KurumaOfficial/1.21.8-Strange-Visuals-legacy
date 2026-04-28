package ru.strange.client.module.impl.other;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

import java.awt.Color;

@IModule(
        name = "Цвет неба",
        description = "Кастомный цвет неба",
        category = Category.Other,
        bind = -1
)
public class SkyColor extends Module {

    private static final int DEFAULT_SKY_COLOR_RGB = new Color(135, 206, 235).getRGB();
    private static volatile SkyColor instance;

    private final ModeSetting preset = new ModeSetting("Режим", "Кастомный",
            "Кастомный", "Красный", "Синий", "Зеленый", "Фиолетовый", "Оранжевый", "Розовый");
    
    private final HueSetting skyColor = new HueSetting("Цвет неба", new Color(135, 206, 235));
    private final SliderSetting blend = new SliderSetting("Сила смешивания", 1.0f, 0.0f, 1.0f, 0.01f, true);
    private final BooleanSetting weatherAdaptive = new BooleanSetting("Учитывать погоду", false);

    public SkyColor() {
        instance = this;
        this.enable = true;  // Включаем по умолчанию
        addSettings(preset, skyColor, blend, weatherAdaptive);
    }

    public static boolean isActiveSkyColor() {
        return instance != null && instance.enable;
    }

    public static int getSkyColorRGB() {
        if (instance == null) return DEFAULT_SKY_COLOR_RGB;
        
        Color c = null;
        String mode = instance.preset.get();
        
        switch (mode) {
            case "Красный" -> c = new Color(220, 80, 80);
            case "Синий" -> c = new Color(80, 120, 220);
            case "Зеленый" -> c = new Color(80, 200, 80);
            case "Фиолетовый" -> c = new Color(160, 80, 200);
            case "Оранжевый" -> c = new Color(220, 140, 50);
            case "Розовый" -> c = new Color(220, 100, 160);
            default -> c = instance.skyColor.getColor();
        }
        
        return c.getRGB() | 0xFF000000;
    }

    public static float getBlendStrength() {
        if (instance == null) return 0.0f;
        float value = instance.blend.get();
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static boolean shouldAdaptWeather() {
        return instance != null && instance.weatherAdaptive.get();
    }
}