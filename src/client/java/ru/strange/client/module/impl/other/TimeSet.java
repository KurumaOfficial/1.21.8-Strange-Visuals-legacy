package ru.strange.client.module.impl.other;

import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.ModeSetting;

@IModule(
        name = "Время суток",
        category = Category.Other,
        description = "Поставить свое время суток",
        bind = -1
)
public class TimeSet extends Module {
    public static ModeSetting timeOfDay = new ModeSetting("Время суток", "Ночь", "День", "Закат", "Рассвет", "Ночь", "Полночь", "Полдень");

    public static long customTime = -1;
    public static boolean isEnabled = false;

    public TimeSet() {
        addSettings(timeOfDay);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        isEnabled = true;
        updateTargetTime();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        isEnabled = false;
        customTime = -1;
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!isEnabled || mc.world == null) {
            return;
        }
        updateTargetTime();
    }

    private void updateTargetTime() {
        String currentMode = timeOfDay.get();

        switch (currentMode) {
            case "День":
                customTime = 1000;
                break;
            case "Закат":
                customTime = 12000;
                break;
            case "Рассвет":
                customTime = 23000;
                break;
            case "Полночь":
                customTime = 13000;
                break;
            case "Ночь":
                customTime = 18000;
                break;
            case "Полдень":
                customTime = 6000;
                break;
            default:
                customTime = 0;
                break;
        }
    }
}
