package ru.strange.client.module.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
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
import ru.strange.client.utils.other.KeyBindPolicy;
import ru.strange.client.utils.other.ServerRestrictionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Module extends Config {
    public IModule module = this.getClass().getAnnotation(IModule.class);
    private static MinecraftClient _mc;
    public static MinecraftClient mc = null;

    private static void ensureMc() {
        if (mc == null) {
            mc = MinecraftClient.getInstance();
        }
    }
    public String name;
    public int bind;
    public boolean enable;
    public boolean open = false;
    public Category category;
    public String displayName;
    public String description;
    public boolean binding;
    public boolean isRender = true;
    private JsonObject defaultState;

    public Module() {
        ensureMc();
        if (module == null) {
            throw new IllegalStateException("Module " + getClass().getName() + " is missing @IModule annotation");
        }
        name = module.name();
        category = module.category();
        bind = KeyBindPolicy.normalizeStoredBind(module.bind() == 0 ? BindSettings.NONE : module.bind());
        enable = false;
        description = module.description();
        displayName = name;
    }

    public void onEnable() {
        ensureMc();
        try {
            EventManager.register(this);
        } catch (RuntimeException e) {
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
        return KeyBindPolicy.getBindName(bind);
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

    public final void captureDefaultState() {
        defaultState = save().deepCopy();
    }

    public final boolean restoreDefaultState() {
        if (defaultState == null) {
            return false;
        }

        return load(defaultState.deepCopy());
    }

    public boolean load(JsonObject object) {
        if (object == null) {
            return false;
        }

        Boolean storedEnable = readBoolean(object.get("enable"));
        boolean shouldEnable = storedEnable != null && storedEnable;
        if (object.has("enable") && storedEnable == null) {
            logInvalidModuleValue("enable", object.get("enable"), "boolean", null);
        }

        if (object.has("keyIndex")) {
            Integer storedBind = readInt(object.get("keyIndex"));
            if (storedBind != null) {
                setBindSilently(storedBind);
            } else {
                logInvalidModuleValue("keyIndex", object.get("keyIndex"), "integer", null);
            }
        }

        JsonObject propertiesObject = object.has("Settings") && object.get("Settings").isJsonObject()
                ? object.getAsJsonObject("Settings")
                : null;
        for (Setting set : getSettings()) {
            if (set == null || propertiesObject == null || !propertiesObject.has(set.name)) {
                continue;
            }

            JsonElement storedValue = propertiesObject.get(set.name);

            if (set instanceof BooleanSetting) {
                Boolean value = readBoolean(storedValue);
                if (value != null) {
                    ((BooleanSetting) set).set(value);
                } else {
                    logInvalidSettingValue(set, storedValue, "boolean", null);
                }
            } else if (set instanceof ModeSetting) {
                ModeSetting modeSetting = (ModeSetting) set;
                String storedMode = readString(storedValue);
                if (storedMode != null) {
                    if (looksLikeShaderThemeSetting(modeSetting)) {
                        storedMode = ShaderThemePreset.normalizeSelectableDisplayName(storedMode);
                    }
                    modeSetting.setMode(storedMode);
                } else {
                    logInvalidSettingValue(set, storedValue, "string", null);
                }
            } else if (set instanceof SliderSetting) {
                SliderSetting sliderSetting = (SliderSetting) set;
                Float value = readFloat(storedValue);
                if (value != null) {
                    sliderSetting.current = clampFinite(value, sliderSetting.minimum, sliderSetting.maximum, sliderSetting.current);
                } else {
                    logInvalidSettingValue(set, storedValue, "number", null);
                }
            } else if (set instanceof BindSettings) {
                Integer value = readInt(storedValue);
                if (value != null) {
                    ((BindSettings) set).setSilently(value);
                } else {
                    logInvalidSettingValue(set, storedValue, "integer", null);
                }
            } else if (set instanceof StringSetting) {
                String value = readString(storedValue);
                if (value != null) {
                    ((StringSetting) set).input = value;
                } else {
                    logInvalidSettingValue(set, storedValue, "string", null);
                }
            } else if (set instanceof HueSetting) {
                HueSetting hueSetting = (HueSetting) set;
                if (storedValue.isJsonObject()) {
                    JsonObject hueObject = storedValue.getAsJsonObject();
                    Float currentValue = readFloat(hueObject.get("current"));
                    if (currentValue != null) {
                        hueSetting.current = clampFinite(currentValue, hueSetting.minimum, hueSetting.maximum, hueSetting.current);
                    } else if (hueObject.has("current")) {
                        logInvalidSettingValue(set, hueObject.get("current"), "number", null);
                    }

                    Float saturationValue = readFloat(hueObject.get("saturation"));
                    if (saturationValue != null) {
                        hueSetting.saturation = clampFinite(saturationValue, 0.0f, 1.0f, hueSetting.saturation);
                    } else if (hueObject.has("saturation")) {
                        logInvalidSettingValue(set, hueObject.get("saturation"), "number", null);
                    }

                    Float brightnessValue = readFloat(hueObject.get("brightness"));
                    if (brightnessValue != null) {
                        hueSetting.brightness = clampFinite(brightnessValue, 0.0f, 1.0f, hueSetting.brightness);
                    } else if (hueObject.has("brightness")) {
                        logInvalidSettingValue(set, hueObject.get("brightness"), "number", null);
                    }
                } else {
                    Float value = readFloat(storedValue);
                    if (value != null) {
                        hueSetting.current = clampFinite(value, hueSetting.minimum, hueSetting.maximum, hueSetting.current);
                    } else {
                        logInvalidSettingValue(set, storedValue, "number or object", null);
                    }
                }
            } else if (set instanceof MultiBooleanSetting) {
                if (storedValue.isJsonObject()) {
                    JsonObject multiBoolObject = storedValue.getAsJsonObject();
                    for (BooleanSetting boolSetting : ((MultiBooleanSetting) set).settings) {
                        if (multiBoolObject.has(boolSetting.name)) {
                            Boolean value = readBoolean(multiBoolObject.get(boolSetting.name));
                            if (value != null) {
                                boolSetting.set(value);
                            } else {
                                logInvalidSettingValue(boolSetting, multiBoolObject.get(boolSetting.name), "boolean", null);
                            }
                        }
                    }
                } else {
                    logInvalidSettingValue(set, storedValue, "object", null);
                }
            } else if (set instanceof ListSetting) {
                ListSetting listSetting = (ListSetting) set;
                List<String> selectedValues = new ArrayList<>();

                if (storedValue.isJsonArray()) {
                    for (var element : storedValue.getAsJsonArray()) {
                        String value = readString(element);
                        if (value != null) {
                            if (listSetting.list.contains(value)) {
                                selectedValues.add(value);
                            }
                        } else {
                            logInvalidSettingValue(set, element, "string", null);
                        }
                    }
                } else {
                    String packedValue = readString(storedValue);
                    if (packedValue == null) {
                        logInvalidSettingValue(set, storedValue, "array or comma-delimited string", null);
                        continue;
                    }

                    String[] split = packedValue.split(",");
                    for (String s : split) {
                        String value = s.trim();
                        if (listSetting.list.contains(value)) {
                            selectedValues.add(value);
                        }
                    }
                }

                listSetting.selected = selectedValues;
            }
        }

        return shouldEnable;
    }

    private void logInvalidModuleValue(String fieldName, JsonElement value, String expectedType, RuntimeException exception) {
        String rawValue = value == null ? "null" : value.toString();
        if (exception == null) {
            Strange.LOGGER.warn("Skipping invalid module field {}.{}: expected {}, got {}", name, fieldName, expectedType, rawValue);
            return;
        }

        Strange.LOGGER.warn("Skipping invalid module field {}.{}: expected {}, got {}",
                name, fieldName, expectedType, rawValue, exception);
    }

    private void logInvalidSettingValue(Setting setting, JsonElement value, String expectedType, RuntimeException exception) {
        String settingName = setting == null ? "<unknown>" : setting.name;
        String rawValue = value == null ? "null" : value.toString();
        if (exception == null) {
            Strange.LOGGER.warn("Skipping invalid setting {}.{}: expected {}, got {}", name, settingName, expectedType, rawValue);
            return;
        }

        Strange.LOGGER.warn("Skipping invalid setting {}.{}: expected {}, got {}",
                name, settingName, expectedType, rawValue, exception);
    }

    private static Boolean readBoolean(JsonElement value) {
        if (!(value instanceof JsonPrimitive primitive)) {
            return null;
        }

        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }

        if (!primitive.isString()) {
            return null;
        }

        String normalized = primitive.getAsString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        return null;
    }

    private static Integer readInt(JsonElement value) {
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return null;
        }

        try {
            return primitive.getAsInt();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Float readFloat(JsonElement value) {
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return null;
        }

        try {
            float resolved = primitive.getAsFloat();
            return Float.isFinite(resolved) ? resolved : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String readString(JsonElement value) {
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
            return null;
        }

        return primitive.getAsString();
    }

    private static float clampFinite(float value, float minimum, float maximum, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean looksLikeShaderThemeSetting(ModeSetting setting) {
        return setting.modes.contains(ShaderThemePreset.COSMOS.displayName())
                && setting.modes.contains(ShaderThemePreset.AURORA.displayName())
                && setting.modes.contains(ShaderThemePreset.BLOOM.displayName());
    }

    public int getBind() {
        return bind;
    }

    public void setBind(int bind) {
        setBindInternal(bind, true);
    }

    public void setBindSilently(int bind) {
        setBindInternal(bind, false);
    }

    public void setState(boolean enable) {
        applyState(enable, true);
    }

    public void setEnable(boolean enable) {
        applyState(enable, false);
    }

    private void setBindInternal(int bind, boolean persist) {
        int normalizedBind = KeyBindPolicy.normalizeStoredBind(bind);
        if (this.bind == normalizedBind) {
            return;
        }

        this.bind = normalizedBind;
        if (persist && Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.autoSave();
        }
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

        if (this.enable != enable) {
            return;
        }

        if (persist) {
            ModuleSounds.playToggle(this, enable);
        }

        if (persist && Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.autoSave();
        }
    }
}
