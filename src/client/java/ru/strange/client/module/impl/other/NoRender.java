package ru.strange.client.module.impl.other;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.MultiBooleanSetting;

@IModule(
        name = "Без рендера",
        description = " ",
        category = Category.Other,
        bind = -1
)
public class NoRender extends Module {

    public static volatile NoRender INSTANCE;

    public static MultiBooleanSetting settings = new MultiBooleanSetting(
            "Настройки",
            new BooleanSetting("Убрать огонь", true),
            new BooleanSetting("Убрать тряску", true),
            new BooleanSetting("Убрать тыкву", true),
            new BooleanSetting("Убрать портал", true),
            new BooleanSetting("Убрать скорборд", false)
    );

    /**
     * Singleton инициализируется через конструктор, вызываемый Manager
     * один раз при загрузке модулей.
     */
    public NoRender() {
        if (INSTANCE != null) {
            throw new IllegalStateException("NoRender module already initialized");
        }
        INSTANCE = this;
        addSettings(settings);
    }

    public static boolean enabled(String name) {
        return INSTANCE != null && INSTANCE.enable && settings.get(name);
    }
}