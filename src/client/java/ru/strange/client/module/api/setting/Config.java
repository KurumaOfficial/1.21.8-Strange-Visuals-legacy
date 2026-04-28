package ru.strange.client.module.api.setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Config {
    private final ArrayList<Setting> settingList = new ArrayList<>();

    public final void addSettings(Setting... var1) {
        this.settingList.addAll(Arrays.asList(var1));
    }

    public final List<Setting> getSettingsForGUI() {
        ArrayList<Setting> visibleSettings = new ArrayList<>(this.settingList.size());
        for (Setting setting : this.settingList) {
            if (!(Boolean) setting.hidden.get()) {
                visibleSettings.add(setting);
            }
        }
        return visibleSettings;
    }

    public final List<Setting> getSettings() {
        return this.settingList;
    }
}
