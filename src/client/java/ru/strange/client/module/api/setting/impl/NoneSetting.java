package ru.strange.client.module.api.setting.impl;

import ru.strange.client.module.api.setting.Setting;

import java.util.function.Supplier;

public class NoneSetting extends Setting {

    public float up;
    public NoneSetting(float up) {
        this.up = up;
    }

    public NoneSetting() {
        this.up = 15;
    }

    public NoneSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }

    public float get() {
        return up;
    }

}
