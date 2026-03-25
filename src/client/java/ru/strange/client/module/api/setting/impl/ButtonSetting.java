package ru.strange.client.module.api.setting.impl;

import ru.strange.client.module.api.setting.Setting;

import java.util.Objects;
import java.util.function.Supplier;

public class ButtonSetting extends Setting {

    public int mode;
    public String description;
    private String actionLabel = "Open";
    private Runnable action = () -> { };

    public ButtonSetting(String name, int mode) {
        this.name = name;
        this.mode = mode;
    }

    public ButtonSetting(String name, int mode, String actionLabel, Runnable action) {
        this(name, mode);
        this.actionLabel = actionLabel == null || actionLabel.isBlank() ? "Open" : actionLabel;
        this.action = action == null ? () -> { } : action;
    }

    public int get() {
        return mode;
    }

    public void set(int mode) {
        this.mode = mode;
    }

    public ButtonSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public ButtonSetting actionLabel(String actionLabel) {
        this.actionLabel = actionLabel == null || actionLabel.isBlank() ? "Open" : actionLabel;
        return this;
    }

    public ButtonSetting action(Runnable action) {
        this.action = action == null ? () -> { } : action;
        return this;
    }

    public void press() {
        Objects.requireNonNullElse(action, () -> { }).run();
    }
}
