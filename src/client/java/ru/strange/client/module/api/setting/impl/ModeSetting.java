package ru.strange.client.module.api.setting.impl;

import ru.strange.client.module.api.setting.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class ModeSetting extends Setting {
    public final List<String> modes;
    public String currentMode;
    public String description;

    public int index;
    public boolean opened;

    public ModeSetting(String name, String currentMode, String... options) {
        this.name = name;
        this.modes = new ArrayList<>(Arrays.asList(options));
        setMode(currentMode);
    }

    public String get() {
        return currentMode;
    }
    public boolean is(String mode) {
        return currentMode.equalsIgnoreCase(mode);
    }

    public void setMode(String mode) {
        String previousMode = this.currentMode;
        if (modes.isEmpty()) {
            this.index = 0;
            this.currentMode = mode == null ? "" : mode;
            return;
        }

        int resolvedIndex = 0;
        if (mode != null) {
            for (int i = 0; i < modes.size(); i++) {
                if (modes.get(i).equalsIgnoreCase(mode)) {
                    resolvedIndex = i;
                    break;
                }
            }
        }

        this.index = resolvedIndex;
        this.currentMode = modes.get(resolvedIndex);
        if (previousMode != null && !previousMode.equalsIgnoreCase(this.currentMode)) {
            triggerAutoSave();
        }
    }

    public void replaceModes(String selectedMode, String... options) {
        modes.clear();
        if (options != null) {
            modes.addAll(Arrays.asList(options));
        }
        setMode(selectedMode);
    }

    public ModeSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }



}
