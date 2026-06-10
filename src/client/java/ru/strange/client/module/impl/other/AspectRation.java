package ru.strange.client.module.impl.other;

import ru.strange.client.Strange;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.utils.math.ScaledResolution;

@IModule(
        name = "Аспект ратион",
        description = "Соотношение экрана",
        category = Category.Other,
        bind = -1
)
public class AspectRation extends Module {
    public static final ModeSetting aspect = new ModeSetting("Соотношение экрана", "16:9", "16:9", "4:3", "1:1", "16:10", "21:9", "32:9", "5:4", "2:1", "Кастомное");
    public static final SliderSetting customAspect = new SliderSetting("Кастомное значение", 2, 1, 3, 0.1F, false)
            .hidden(() -> !aspect.is("Кастомное"));

    public AspectRation() {
        addSettings(aspect, customAspect);
    }

    public static float getAspectRation() {
        if (Strange.get == null || Strange.get.manager == null || mc == null || mc.getWindow() == null) {
            return 0.0F;
        }

        Module module = Strange.get.manager.getModule(AspectRation.class);
        if (module == null || !module.enable) {
            return 0.0F;
        }

        if (mc.getWindow().isMinimized() || mc.getWindow().hasZeroWidthOrHeight()) {
            return 0.0F;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        if (sr.getHeight() <= 0) {
            return 0.0F;
        }

        float aspect1 = (float) sr.getWidth() / (float) sr.getHeight();
        if (!Float.isFinite(aspect1) || aspect1 <= 0.0F) {
            return 0.0F;
        }

        float newAspect = switch (aspect.get()) {
            case "16:9" -> 16F / 9F;
            case "4:3" -> 4F / 3F;
            case "1:1" -> 1F;
            case "16:10" -> 16F / 10F;
            case "21:9" -> 21F / 9F;
            case "32:9" -> 32F / 9F;
            case "5:4" -> 5F / 4F;
            case "2:1" -> 2F;
            default -> customAspect.get();
        };

        if (!Float.isFinite(newAspect) || newAspect <= 0.0F) {
            return 0.0F;
        }

        float result = newAspect - aspect1;
        return Float.isFinite(result) ? result : 0.0F;
    }
}
