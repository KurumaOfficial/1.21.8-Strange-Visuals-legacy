package ru.strange.client.module.api.setting.impl;

import ru.strange.client.module.api.setting.Setting;

import java.util.LinkedHashSet;
import java.util.function.Supplier;

public class BooleanSetting extends Setting {

    private boolean state;
    public String description;
//    public Animation anim = new Animation();
//    public Animation animation = new EaseInOutQuad(300, 1);
//    public Animation animation2 = new EaseInOutQuad(300, 1);

    public BooleanSetting(String name, boolean state) {
        this.name = name;
        this.state = state;
        this.description = null;
    }

    public boolean get() {
        return state;
    }

    public void set(boolean state) {
        this.state = state;
        triggerAutoSave();
    }

    public BooleanSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }

    public BooleanSetting describe(String description) {
        this.description = description;
        return this;
    }


}
