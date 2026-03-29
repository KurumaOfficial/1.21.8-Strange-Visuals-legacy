package ru.strange.client.module.api.setting.impl;

import ru.strange.client.module.api.setting.Setting;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class MultiBooleanSetting extends Setting {
    public List<BooleanSetting> settings;
    public boolean opened;

    public MultiBooleanSetting(String name, BooleanSetting... settings) {
        this.name = name;
        this.settings = Arrays.asList(settings);
    }

    public boolean get(String name) {
        for (BooleanSetting setting : settings) {
            if (setting.name.equals(name)) {
                return setting.get();
            }
        }
        return false;
    }

    public boolean getByIndex(int index) {
        if (index < 0 || index >= settings.size()) {
            return false;
        }
        return settings.get(index).get();
    }

    public MultiBooleanSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }
}






