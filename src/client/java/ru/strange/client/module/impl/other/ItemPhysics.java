package ru.strange.client.module.impl.other;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Физика предметов",
        description = "",
        category = Category.Other,
        bind = -1
)
public class ItemPhysics extends Module {

    private static ItemPhysics instance;

    private final SliderSetting itemScale = new SliderSetting("Размер предмета", 0.75f, 0.3f, 1.0f, 0.05f, false);

    public ItemPhysics() {
        instance = this;
        addSettings(itemScale);
    }

    public static ItemPhysics getInstance() {
        return instance;
    }

    public static boolean isActivePhysics() {
        return instance != null && instance.enable;
    }

    public static float getItemScale() {
        return instance == null ? 0.75f : instance.itemScale.get();
    }
}