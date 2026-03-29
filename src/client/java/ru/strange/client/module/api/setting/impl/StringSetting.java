package ru.strange.client.module.api.setting.impl;

import ru.strange.client.module.api.setting.Setting;

import java.util.function.Supplier;

public class StringSetting extends Setting {

    public String input;
    public String description;
//    public Animation animation = new EaseInOutQuad(300, 1);
//    public Animation animation2 = new EaseInOutQuad(300, 1);
    public boolean active;

    public StringSetting(String name, String input) {
        this.name = name;
        this.input = input;
    }

    public String get() {
        return input;
    }

    public void set(String input) {
        this.input = input;
        triggerAutoSave();
    }

    public StringSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }


}

