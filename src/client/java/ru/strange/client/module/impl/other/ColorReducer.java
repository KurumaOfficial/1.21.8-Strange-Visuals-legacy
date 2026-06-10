package ru.strange.client.module.impl.other;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.event.Event;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Убавление цветов",
        description = "Уменьшает насыщенность цветов для лучшей видимости",
        category = Category.Other,
        bind = -1
)
public class ColorReducer extends Module {

    private final SliderSetting saturation = new SliderSetting("Насыщенность", 0.5f, 0.0f, 1.0f, 0.05f, false);
    private final SliderSetting brightness = new SliderSetting("Яркость", 0.9f, 0.5f, 1.5f, 0.05f, false);

    public ColorReducer() {
        addSettings(saturation, brightness);
    }

    public float getSaturation() {
        return saturation.get();
    }

    public float getBrightness() {
        return brightness.get();
    }
}
