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

    public static MultiBooleanSetting settings = new MultiBooleanSetting(
            "Настройки",
            new BooleanSetting("Убрать огонь", true),
            new BooleanSetting("Убрать тряску", true),
            new BooleanSetting("Убрать тыкву", true),
            new BooleanSetting("Убрать портал", false)
    );

    public NoRender() {
        addSettings(settings);
    }
}
