package ru.strange.client.module.api.setting;

import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;

import java.util.function.Supplier;

public class Setting extends Config {
    public String name;
    public Supplier<Boolean> hidden = () -> false;

    public String getDisplayName() {
        return ModLocalization.raw(name);
    }

    public void triggerAutoSave() {
        if (Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.autoSave();
        }
    }

    public void triggerDeferredAutoSave() {
        if (Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.markDirty();
        }
    }
}
