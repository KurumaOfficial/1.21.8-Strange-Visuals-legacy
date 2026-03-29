package ru.strange.client.module.impl.other;

import ru.strange.client.Strange;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.utils.other.SoundUtil;

@IModule(
        name = "Module Sounds",
        description = "toggle and utility sound settings",
        category = Category.Other,
        bind = -1
)
public class ModuleSounds extends Module {
    private final BooleanSetting moduleToggles = new BooleanSetting("Звуки модулей", true);
    private final ModeSetting toggleProfile = new ModeSetting("Профиль", "Function", "Function", "Classic");
    private final SliderSetting toggleVolume = new SliderSetting("Громкость", 0.65f, 0.0f, 1.0f, 0.05f, true);

    public ModuleSounds() {
        enable = true;
        addSettings(moduleToggles, toggleProfile, toggleVolume);
    }

    public static void playToggle(Module module, boolean enabled) {
        ModuleSounds settings = getInstance();
        if (settings == null || !settings.enable || !settings.moduleToggles.get()) {
            return;
        }

        String sound = settings.toggleProfile.is("Classic")
                ? (enabled ? "on" : "off")
                : (enabled ? "function_on" : "function_off");
        SoundUtil.playSound_wav(sound, settings.toggleVolume.get());
    }

    private static ModuleSounds getInstance() {
        if (Strange.get == null || Strange.get.manager == null) {
            return null;
        }
        return Strange.get.manager.get(ModuleSounds.class);
    }
}
