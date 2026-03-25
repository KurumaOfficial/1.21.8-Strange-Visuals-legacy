package ru.strange.client.module.impl.other;

import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventScreen;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.renderengine.renderers.util.ScreenFilterRenderer;

import java.awt.*;

@IModule(
        name = "Screen Filters",
        description = "пост-фильтры экрана",
        category = Category.Other,
        bind = -1
)
public class ScreenFilters extends Module {

    private final SliderSetting saturation = new SliderSetting("Насыщенность", 1.0f, 0.0f, 2.2f, 0.05f, false);
    private final SliderSetting exposure = new SliderSetting("Экспозиция", 1.0f, 0.3f, 2.3f, 0.05f, false);
    private final SliderSetting gamma = new SliderSetting("Гамма", 1.0f, 0.4f, 2.2f, 0.05f, false);
    private final SliderSetting contrast = new SliderSetting("Контраст", 1.0f, 0.4f, 2.0f, 0.05f, false);
    private final SliderSetting brightness = new SliderSetting("Яркость", 0.0f, -0.4f, 0.4f, 0.02f, false);
    private final SliderSetting vignette = new SliderSetting("Виньетка", 0.0f, 0.0f, 1.0f, 0.05f, false);
    private final SliderSetting grain = new SliderSetting("Зерно", 0.0f, 0.0f, 1.0f, 0.05f, false);
    private final BooleanSetting useTint = new BooleanSetting("Оттенок", false);
    private final HueSetting tintColor = new HueSetting("Цвет оттенка", new Color(255, 235, 210)).hidden(() -> !useTint.get());
    private final SliderSetting tintIntensity = new SliderSetting("Сила оттенка", 0.12f, 0.0f, 1.0f, 0.05f, false).hidden(() -> !useTint.get());

    public ScreenFilters() {
        addSettings(saturation, exposure, gamma, contrast, brightness, vignette, grain, useTint, tintColor, tintIntensity);
    }

    @EventInit
    public void onScreen(EventScreen event) {
        if (!enable || mc.player == null || mc.world == null) {
            return;
        }

        ScreenFilterRenderer renderer = ScreenFilterRenderer.getInstance();
        renderer.setSaturation(saturation.get());
        renderer.setExposure(exposure.get());
        renderer.setGamma(gamma.get());
        renderer.setContrast(contrast.get());
        renderer.setBrightness(brightness.get());
        renderer.setVignette(vignette.get());
        renderer.setGrain(grain.get());
        renderer.setTintColor(tintColor.getRGB());
        renderer.setTintIntensity(useTint.get() ? tintIntensity.get() : 0.0f);
        renderer.render();
    }

    @Override
    public void onDisable() {
        ScreenFilterRenderer.getInstance().close();
        super.onDisable();
    }
}
