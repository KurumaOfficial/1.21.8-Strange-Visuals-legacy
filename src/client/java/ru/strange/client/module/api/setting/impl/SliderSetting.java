package ru.strange.client.module.api.setting.impl;

import ru.strange.client.module.api.setting.Setting;

import java.util.function.Supplier;

public class SliderSetting extends Setting {
    public float current, minimum, maximum, increment;
    public float sliderWidth;
    public boolean sliding;
    public boolean percent;
    public String description;

    public SliderSetting(String name, float current, float minimum, float maximum, float increment, boolean percent) {
        this.name = name;
        this.minimum = minimum;
        this.current = current;
        this.maximum = maximum;
        this.increment = increment;
        this.description = null;
        this.percent = percent;
    }

    public float get() {
        return current;
    }

    public SliderSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }

    public SliderSetting describe(String description) {
        this.description = description;
        return this;
    }


}
