package ru.strange.client.module.impl.other;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.HueSetting;

import java.awt.*;

@IModule(
        name = "Кастомный туман",
        description = "Настраиваемый цвет тумана",
        category = Category.Other,
        bind = -1
)
public class CustomFog extends Module {

    private static final int DEFAULT_FOG_COLOR_RGB = new Color(200, 200, 210).getRGB();

    private static volatile CustomFog instance;

    private final HueSetting fogColor = new HueSetting("Цвет тумана", new Color(200, 200, 210));

    /**
     * Singleton инициализируется через конструктор, вызываемый Manager
     * один раз при загрузке модулей.
     */
    public CustomFog() {
        if (instance != null) {
            throw new IllegalStateException("CustomFog module already initialized");
        }
        instance = this;
        addSettings(fogColor);
    }

    public static CustomFog getInstance() {
        return instance;
    }

    public static boolean isActiveFog() {
        return instance != null && instance.enable;
    }

    public static int getFogColorRGB() {
        return instance == null ? DEFAULT_FOG_COLOR_RGB : instance.fogColor.getRGB();
    }

    public static float getFogBlendStrength() {
        if (instance == null || !instance.enable) {
            return 0.0f;
        }

        return 0.72f;
    }
}