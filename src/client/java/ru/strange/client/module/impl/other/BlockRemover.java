package ru.strange.client.module.impl.other;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.MultiBooleanSetting;

@IModule(
        name = "Удаление блоков",
        description = "Удаляет траву, тростник и другие блоки для лучшей видимости",
        category = Category.Other,
        bind = -1
)
public class BlockRemover extends Module {

    public static volatile BlockRemover INSTANCE;

    public final MultiBooleanSetting settings = new MultiBooleanSetting(
            "Блоки",
            new BooleanSetting("Трава", true),
            new BooleanSetting("Тростник", true),
            new BooleanSetting("Листва", false),
            new BooleanSetting("Цветы", false),
            new BooleanSetting("Грибы", false),
            new BooleanSetting("Виноград", false),
            new BooleanSetting("Сладкие ягоды", false),
            new BooleanSetting("Мхи", false)
    );

    public BlockRemover() {
        if (INSTANCE != null) {
            throw new IllegalStateException("BlockRemover module already initialized");
        }
        INSTANCE = this;
        addSettings(settings);
    }

    public static boolean shouldRemove(String blockName) {
        if (INSTANCE == null || !INSTANCE.enable) {
            return false;
        }

        String lowerName = blockName.toLowerCase();
        
        if (INSTANCE.settings.get("Трава") && (lowerName.contains("grass") || lowerName.contains("tall_grass"))) {
            return true;
        }
        if (INSTANCE.settings.get("Тростник") && lowerName.contains("sugar_cane")) {
            return true;
        }
        if (INSTANCE.settings.get("Листва") && lowerName.contains("leaves")) {
            return true;
        }
        if (INSTANCE.settings.get("Цветы") && (lowerName.contains("flower") || lowerName.contains("rose") || lowerName.contains("tulip"))) {
            return true;
        }
        if (INSTANCE.settings.get("Грибы") && lowerName.contains("mushroom")) {
            return true;
        }
        if (INSTANCE.settings.get("Виноград") && lowerName.contains("cave_vines")) {
            return true;
        }
        if (INSTANCE.settings.get("Сладкие ягоды") && lowerName.contains("sweet_berry")) {
            return true;
        }
        if (INSTANCE.settings.get("Мхи") && lowerName.contains("moss")) {
            return true;
        }

        return false;
    }
}
