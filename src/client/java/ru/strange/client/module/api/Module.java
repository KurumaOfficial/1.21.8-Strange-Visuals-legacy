package ru.strange.client.module.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.event.EventManager;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.api.setting.Config;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ListSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.MultiBooleanSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.module.api.setting.impl.StringSetting;
import ru.strange.client.module.impl.other.ModuleSounds;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.utils.other.ServerRestrictionManager;

import java.util.ArrayList;

public class Module extends Config {
    public IModule module = this.getClass().getAnnotation(IModule.class);
    public static MinecraftClient mc = MinecraftClient.getInstance();
    public String name;
    public int bind;
    public boolean enable;
    public boolean open = false;
    public Category category;
    public String displayName;
    public String description;
    public boolean binding;
    public boolean isRender = true;

    public Module() {
        name = module.name();
        category = module.category();
        bind = module.bind() == 0 ? -1 : module.bind();
        enable = false;
        description = module.description();
        displayName = name;
    }

    public void onEnable() {
        try {
            EventManager.register(this);
        } catch (Exception e) {
            Strange.LOGGER.error("Failed to enable module {}", name, e);
            enable = false;
        }
    }

    public void onDisable() {
        EventManager.unregister(this);
    }

    public String getDisplayName() {
        return ModLocalization.raw(displayName);
    }

    public String getLocalizedName() {
        return ModLocalization.raw(name);
    }

    public String getLocalizedDescription() {
        return ModLocalization.raw(description);
    }

    public String getBindName() {
        if (bind == -1) {
            return "NONE";
        }

        if (bind >= BindSettings.MOUSE_OFFSET) {
            int button = bind - BindSettings.MOUSE_OFFSET;
            return switch (button) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "M1";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "M2";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "M3";
                case 3 -> "M4";
                case 4 -> "M5";
                case 5 -> "M6";
                case 6 -> "M7";
                case 7 -> "M8";
                default -> "M" + (button + 1);
            };
        }

        try {
            return InputUtil.fromKeyCode(bind, -1).getLocalizedText().getString();
        } catch (Exception e) {
            return "NONE";
        }
    }

    public void toggle() {
        applyState(!enable, true);
    }

    public JsonObject save() {
        JsonObject object = new JsonObject();
        object.addProperty("enable", enable);
        object.addProperty("keyIndex", this.bind);

        JsonObject propertiesObject = new JsonObject();
        for (Setting set : getSettings()) {
            if (set instanceof BooleanSetting) {
                propertiesObject.addProperty(set.name, ((BooleanSetting) set).get());
            } else if (set instanceof ModeSetting) {
                propertiesObject.addProperty(set.name, ((ModeSetting) set).currentMode);
            } else if (set instanceof SliderSetting) {
                propertiesObject.addProperty(set.name, ((SliderSetting) set).current);
            } else if (set instanceof BindSettings) {
                propertiesObject.addProperty(set.name, ((BindSettings) set).key);
            } else if (set instanceof StringSetting) {
                propertiesObject.addProperty(set.name, ((StringSetting) set).input);
            } else if (set instanceof HueSetting) {
                HueSetting hueSetting = (HueSetting) set;
                JsonObject hueObject = new JsonObject();
                hueObject.addProperty("current", hueSetting.current);
                hueObject.addProperty("saturation", hueSetting.saturation);
                hueObject.addProperty("brightness", hueSetting.brightness);
                propertiesObject.add(set.name, hueObject);
            } else if (set instanceof MultiBooleanSetting) {
                JsonObject multiBoolObject = new JsonObject();
                for (BooleanSetting boolSetting : ((MultiBooleanSetting) set).settings) {
                    multiBoolObject.addProperty(boolSetting.name, boolSetting.get());
                }
                propertiesObject.add(set.name, multiBoolObject);
            } else if (set instanceof ListSetting listSetting) {
                JsonArray array = new JsonArray();
                for (String selectedValue : listSetting.selected) {
                    array.add(selectedValue);
                }
                propertiesObject.add(set.name, array);
            }
        }
        object.add("Settings", propertiesObject);
        return object;
    }

    public boolean load(JsonObject object) {
        if (object == null) {
            return false;
        }

        boolean shouldEnable = object.has("enable") && object.get("enable").getAsBoolean();

        if (object.has("keyIndex")) {
            bind = object.get("keyIndex").getAsInt();
        }

        JsonObject propertiesObject = object.getAsJsonObject("Settings");
        for (Setting set : getSettings()) {
            if (set == null || propertiesObject == null || !propertiesObject.has(set.name)) {
                continue;
            }

            if (set instanceof BooleanSetting) {
                ((BooleanSetting) set).set(propertiesObject.get(set.name).getAsBoolean());
            } else if (set instanceof ModeSetting) {
                ModeSetting modeSetting = (ModeSetting) set;
                String storedMode = propertiesObject.get(set.name).getAsString();
                if (looksLikeShaderThemeSetting(modeSetting)) {
                    storedMode = ShaderThemePreset.normalizeSelectableDisplayName(storedMode);
                }
                modeSetting.setMode(storedMode);
            } else if (set instanceof SliderSetting) {
                ((SliderSetting) set).current = propertiesObject.get(set.name).getAsFloat();
            } else if (set instanceof BindSettings) {
                ((BindSettings) set).key = propertiesObject.get(set.name).getAsInt();
            } else if (set instanceof StringSetting) {
                ((StringSetting) set).input = propertiesObject.get(set.name).getAsString();
            } else if (set instanceof HueSetting) {
                HueSetting hueSetting = (HueSetting) set;
                if (propertiesObject.get(set.name).isJsonObject()) {
                    JsonObject hueObject = propertiesObject.getAsJsonObject(set.name);
                    if (hueObject.has("current")) hueSetting.current = hueObject.get("current").getAsFloat();
                    if (hueObject.has("saturation")) hueSetting.saturation = hueObject.get("saturation").getAsFloat();
                    if (hueObject.has("brightness")) hueSetting.brightness = hueObject.get("brightness").getAsFloat();
                } else {
                    hueSetting.current = propertiesObject.get(set.name).getAsFloat();
                }
            } else if (set instanceof MultiBooleanSetting) {
                if (propertiesObject.get(set.name).isJsonObject()) {
                    JsonObject multiBoolObject = propertiesObject.getAsJsonObject(set.name);
                    for (BooleanSetting boolSetting : ((MultiBooleanSetting) set).settings) {
                        if (multiBoolObject.has(boolSetting.name)) {
                            boolSetting.set(multiBoolObject.get(boolSetting.name).getAsBoolean());
                        }
                    }
                }
            } else if (set instanceof ListSetting) {
                ((ListSetting) set).selected = new ArrayList<>();

                if (propertiesObject.get(set.name).isJsonArray()) {
                    for (var element : propertiesObject.getAsJsonArray(set.name)) {
                        String value = element.getAsString();
                        if (((ListSetting) set).list.contains(value)) {
                            ((ListSetting) set).selected.add(value);
                        }
                    }
                } else {
                    String[] split = propertiesObject.get(set.name).getAsString().split(",");
                    for (String s : split) {
                        String value = s.trim();
                        if (((ListSetting) set).list.contains(value)) {
                            ((ListSetting) set).selected.add(value);
                        }
                    }
                }
            }
        }

        return shouldEnable;
    }

    private static boolean looksLikeShaderThemeSetting(ModeSetting setting) {
        return setting.modes.contains(ShaderThemePreset.COSMOS.displayName())
                && setting.modes.contains(ShaderThemePreset.AURORA.displayName())
                && setting.modes.contains(ShaderThemePreset.BLOOM.displayName());
    }

    public int getBind() {
        return bind;
    }

    public void setState(boolean enable) {
        applyState(enable, true);
    }

    public void setEnable(boolean enable) {
        applyState(enable, false);
    }

    private void applyState(boolean enable, boolean persist) {
        if (this.enable == enable) {
            return;
        }

        if (enable && ServerRestrictionManager.preventEnable(this, persist)) {
            return;
        }

        this.enable = enable;
        if (enable) {
            onEnable();
        } else {
            onDisable();
        }

        if (persist) {
            ModuleSounds.playToggle(this, enable);
        }

        if (persist && Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.autoSave();
        }
    }
}
