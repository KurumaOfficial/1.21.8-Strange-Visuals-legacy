package ru.strange.client.utils.other;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemShaderProfiles {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();
    private static final long RELOAD_CHECK_INTERVAL_MS = 1000L;
    private static final long SAVE_DEBOUNCE_MS = 350L;
    private static final File CONFIG_FILE = new File(Strange.root, "item-shaders.json");
    private static final Set<String> LOGGED_INVALID_CONFIG_VALUES = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_INVALID_ITEM_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_DUPLICATE_ITEM_IDS = ConcurrentHashMap.newKeySet();

    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean dirty;
    private static long lastModified = Long.MIN_VALUE;
    private static long lastReloadCheckAt;
    private static long nextSaveAt = Long.MIN_VALUE;
    private static Map<String, ShaderProfile> profiles = Map.of();

    private ItemShaderProfiles() {
    }

    public static synchronized void ensureLoaded() {
        if (initialized) {
            return;
        }

        initialized = true;
        loadOrCreate();
    }

    public static synchronized ShaderProfile find(ItemStack stack) {
        ensureLoaded();

        if (!enabled || stack == null || stack.isEmpty()) {
            return null;
        }

        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return itemId == null ? null : profiles.get(normalizeItemId(itemId.toString()));
    }

    public static synchronized ShaderProfile find(String itemId) {
        ensureLoaded();

        String normalizedId = normalizeItemId(itemId);
        if (normalizedId == null) {
            return null;
        }

        return profiles.get(normalizedId);
    }

    public static synchronized Map<String, ShaderProfile> snapshot() {
        ensureLoaded();
        return new LinkedHashMap<>(profiles);
    }

    public static synchronized boolean hasOverride(String itemId) {
        ensureLoaded();

        String normalizedId = normalizeItemId(itemId);
        if (normalizedId == null) {
            return false;
        }

        return profiles.containsKey(normalizedId);
    }

    public static synchronized void tick() {
        ensureLoaded();
        flushPendingSave();
        reloadIfModified();
    }

    public static synchronized void setTheme(String itemId, ShaderThemePreset theme) {
        String normalizedId = normalizeItemId(itemId);
        if (normalizedId == null || theme == null) {
            return;
        }

        ensureLoaded();
        reloadIfModified();

        ShaderProfile existing = profiles.get(normalizedId);
        ShaderProfile updated = existing == null
                ? new ShaderProfile(normalizedId, theme.displayName(), null, null, null, null)
                : new ShaderProfile(
                        normalizedId,
                        theme.displayName(),
                        existing.tintColor(),
                        existing.tintMix(),
                        existing.pulseAlpha(),
                        existing.pulseEffectOnly()
                );

        Map<String, ShaderProfile> mutable = new LinkedHashMap<>(profiles);
        mutable.put(normalizedId, updated);
        profiles = Map.copyOf(mutable);
        scheduleSave();
    }

    public static synchronized void clear(String itemId) {
        String normalizedId = normalizeItemId(itemId);
        if (normalizedId == null) {
            return;
        }

        ensureLoaded();
        reloadIfModified();

        Map<String, ShaderProfile> mutable = new LinkedHashMap<>(profiles);
        if (mutable.remove(normalizedId) != null) {
            profiles = Map.copyOf(mutable);
            scheduleSave();
        }
    }

    public static File getConfigFile() {
        return CONFIG_FILE;
    }

    private static String normalizeItemId(String itemId) {
        return normalizeItemId(itemId, false);
    }

    private static String normalizeItemId(String itemId, boolean logInvalid) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        String normalized = itemId.trim().toLowerCase(Locale.ROOT);
        try {
            return Identifier.of(normalized).toString();
        } catch (IllegalArgumentException exception) {
            if (logInvalid) {
                logInvalidItemIdOnce(itemId, exception);
            }
            return null;
        }
    }

    private static void reloadIfModified() {
        if (dirty) {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastReloadCheckAt) < RELOAD_CHECK_INTERVAL_MS) {
            return;
        }
        lastReloadCheckAt = now;

        if (!CONFIG_FILE.exists() || Files.isRegularFile(tempConfigPath())) {
            loadOrCreate();
            return;
        }

        long currentModified = CONFIG_FILE.lastModified();
        if (currentModified != lastModified) {
            loadOrCreate();
        }
    }

    private static void loadOrCreate() {
        try {
            Path configPath = CONFIG_FILE.toPath();
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            recoverTemporaryConfigIfNeeded();
            if (!CONFIG_FILE.exists()) {
                writeDefaultConfig();
            }

            loadFromDisk();
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to prepare item shader config at {}", CONFIG_FILE.getAbsolutePath(), e);
            enabled = true;
            profiles = Map.of();
            lastModified = CONFIG_FILE.exists() ? CONFIG_FILE.lastModified() : Long.MIN_VALUE;
        }
    }

    private static void writeDefaultConfig() throws IOException {
        enabled = true;
        Map<String, ShaderProfile> defaults = new LinkedHashMap<>();
        defaults.put("minecraft:diamond_sword", new ShaderProfile("minecraft:diamond_sword", "COSMOS", parseColor("#8473FF"), 0.18f, null, null));
        defaults.put("minecraft:netherite_sword", new ShaderProfile("minecraft:netherite_sword", "AURORA", parseColor("#7BF5D2"), 0.22f, null, null));
        defaults.put("minecraft:totem_of_undying", new ShaderProfile("minecraft:totem_of_undying", "PULSE_PLASMA", parseColor("#FFD27C"), 0.30f, 0.85f, true));
        profiles = Map.copyOf(defaults);
        saveCurrentConfig();
    }

    private static JsonObject createProfileJson(String theme, String tintColor, Float tintMix, Float pulseAlpha, Boolean pulseEffectOnly) {
        JsonObject profile = new JsonObject();
        profile.addProperty("theme", theme);
        if (tintColor != null) {
            profile.addProperty("tintColor", tintColor);
        }
        if (tintMix != null) {
            profile.addProperty("tintMix", tintMix);
        }
        if (pulseAlpha != null) {
            profile.addProperty("pulseAlpha", pulseAlpha);
        }
        if (pulseEffectOnly != null) {
            profile.addProperty("pulseEffectOnly", pulseEffectOnly);
        }
        return profile;
    }

    private static void loadFromDisk() {
        boolean previousEnabled = enabled;
        Map<String, ShaderProfile> previousProfiles = profiles;
        JsonObject root = loadConfigRoot();
        if (root == null) {
            enabled = previousProfiles.isEmpty() ? true : previousEnabled;
            profiles = previousProfiles.isEmpty() ? Map.of() : previousProfiles;
            lastModified = CONFIG_FILE.exists() ? CONFIG_FILE.lastModified() : Long.MIN_VALUE;
            dirty = false;
            nextSaveAt = Long.MIN_VALUE;
            return;
        }

        enabled = readEnabledFlag(root, previousProfiles.isEmpty() ? true : previousEnabled);
        profiles = parseProfiles(root);
        lastModified = CONFIG_FILE.exists() ? CONFIG_FILE.lastModified() : Long.MIN_VALUE;
        dirty = false;
        nextSaveAt = Long.MIN_VALUE;
    }

    private static void saveCurrentConfig() {
        Path configPath = CONFIG_FILE.toPath();
        Path tempPath = tempConfigPath();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("enabled", enabled);
            root.addProperty("_about", "Maps item ids to Glass Hand shader presets for held items.");

            JsonObject itemsObject = new JsonObject();
            List<String> keys = new ArrayList<>(profiles.keySet());
            keys.sort(String::compareToIgnoreCase);
            for (String key : keys) {
                ShaderProfile profile = profiles.get(key);
                if (profile == null) {
                    continue;
                }
                itemsObject.add(key, createProfileJson(
                        profile.themeName(),
                        profile.tintColor() == null ? null : String.format("#%06X", profile.tintColor() & 0xFFFFFF),
                        profile.tintMix(),
                        profile.pulseAlpha(),
                        profile.pulseEffectOnly()
                ));
            }
            root.add("items", itemsObject);

            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            nextSaveAt = Long.MIN_VALUE;
            lastModified = CONFIG_FILE.lastModified();
            lastReloadCheckAt = System.currentTimeMillis();
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to save item shader profiles {}", CONFIG_FILE.getAbsolutePath(), e);
            nextSaveAt = System.currentTimeMillis() + SAVE_DEBOUNCE_MS;
        }
    }

    private static void scheduleSave() {
        dirty = true;
        nextSaveAt = System.currentTimeMillis() + SAVE_DEBOUNCE_MS;
    }

    private static void flushPendingSave() {
        if (!dirty || nextSaveAt == Long.MIN_VALUE) {
            return;
        }

        if (System.currentTimeMillis() < nextSaveAt) {
            return;
        }

        saveCurrentConfig();
    }

    private static Map<String, ShaderProfile> parseProfiles(JsonObject root) {
        if (root == null || !root.has("items") || !root.get("items").isJsonObject()) {
            return Map.of();
        }

        Map<String, ShaderProfile> result = new LinkedHashMap<>();
        JsonObject itemsObject = root.getAsJsonObject("items");
        for (Map.Entry<String, JsonElement> entry : itemsObject.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            String itemId = normalizeItemId(entry.getKey(), true);
            if (itemId == null) {
                continue;
            }

            ShaderProfile profile = parseProfile(itemId, entry.getValue().getAsJsonObject());
            if (profile != null) {
                if (result.containsKey(itemId)) {
                    logDuplicateItemIdOnce(itemId, entry.getKey());
                }
                result.put(itemId, profile);
            }
        }

        return Map.copyOf(result);
    }

    private static boolean readEnabledFlag(JsonObject root, boolean fallback) {
        if (root == null || !root.has("enabled")) {
            return fallback;
        }

        JsonElement enabledValue = root.get("enabled");
        if (enabledValue == null || !enabledValue.isJsonPrimitive()) {
            logInvalidProfileValueOnce("<root>", "enabled", enabledValue, "boolean", null);
            return fallback;
        }

        try {
            return enabledValue.getAsBoolean();
        } catch (RuntimeException exception) {
            logInvalidProfileValueOnce("<root>", "enabled", enabledValue, "boolean", exception);
            return fallback;
        }
    }

    private static ShaderProfile parseProfile(String itemId, JsonObject object) {
        String theme = readString(object, "theme", "preset", "shader");
        Integer tintColor = readColor(itemId, object, "tintColor", "tint", "color");
        Float tintMix = readFloat(itemId, object, "tintMix", "mix");
        Float pulseAlpha = readFloat(itemId, object, "pulseAlpha", "alpha");
        Boolean pulseEffectOnly = readBoolean(itemId, object, "pulseEffectOnly", "effectOnly");

        return new ShaderProfile(itemId, theme, tintColor, tintMix, pulseAlpha, pulseEffectOnly);
    }

    private static String readString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }

            String value = object.get(key).getAsString();
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private static Float readFloat(String itemId, JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }

            try {
                return object.get(key).getAsFloat();
            } catch (RuntimeException exception) {
                logInvalidProfileValueOnce(itemId, key, object.get(key), "number", exception);
            }
        }

        return null;
    }

    private static Boolean readBoolean(String itemId, JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }

            try {
                return object.get(key).getAsBoolean();
            } catch (RuntimeException exception) {
                logInvalidProfileValueOnce(itemId, key, object.get(key), "boolean", exception);
            }
        }

        return null;
    }

    private static Integer readColor(String itemId, JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }

            try {
                if (object.get(key).getAsJsonPrimitive().isNumber()) {
                    int value = object.get(key).getAsInt();
                    return (value & 0xFF000000) == 0 ? 0xFF000000 | value : value;
                }

                String raw = object.get(key).getAsString();
                Integer parsed = parseColor(raw);
                if (parsed != null) {
                    return parsed;
                }
                logInvalidProfileValueOnce(itemId, key, object.get(key), "hex color", null);
            } catch (RuntimeException exception) {
                logInvalidProfileValueOnce(itemId, key, object.get(key), "hex color", exception);
            }
        }

        return null;
    }

    private static Integer parseColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }

        try {
            long parsed = Long.parseLong(normalized, 16);
            if (normalized.length() <= 6) {
                parsed |= 0xFF000000L;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record ShaderProfile(String itemId, String themeName, Integer tintColor, Float tintMix, Float pulseAlpha, Boolean pulseEffectOnly) {
        public ShaderThemePreset resolveTheme(ShaderThemePreset fallback) {
            if (themeName == null || themeName.isBlank()) {
                return fallback;
            }

            ShaderThemePreset byName = ShaderThemePreset.findByName(themeName);
            if (byName != null) {
                return byName;
            }

            String normalized = themeName
                    .trim()
                    .toUpperCase(Locale.ROOT)
                    .replace(' ', '_')
                    .replace('-', '_')
                    .replace("•", "_");
            while (normalized.contains("__")) {
                normalized = normalized.replace("__", "_");
            }

            try {
                return ShaderThemePreset.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                logInvalidThemeNameOnce(itemId, themeName);
                return fallback;
            }
        }

        public int resolveTintColor(int fallback) {
            return tintColor != null ? tintColor : fallback;
        }

        public float resolveTintMix(float fallback) {
            return tintMix != null ? Math.max(0.0f, Math.min(1.0f, tintMix)) : fallback;
        }

        public float resolvePulseAlpha(float fallback) {
            return pulseAlpha != null ? Math.max(0.0f, pulseAlpha) : fallback;
        }

        public boolean resolvePulseEffectOnly(boolean fallback) {
            return pulseEffectOnly != null ? pulseEffectOnly : fallback;
        }
    }

    private static JsonObject loadConfigRoot() {
        Path configPath = CONFIG_FILE.toPath();
        JsonObject root = tryReadConfigRoot(configPath, false);
        if (root != null) {
            return root;
        }

        Path tempPath = tempConfigPath();
        root = tryReadConfigRoot(tempPath, true);
        if (root == null) {
            return null;
        }

        Strange.LOGGER.warn("Recovered item shader profiles from temporary file {}", tempPath.toAbsolutePath());
        promoteRecoveredTempConfig(tempPath, configPath);
        return root;
    }

    private static JsonObject tryReadConfigRoot(Path path, boolean temporaryFile) {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                Strange.LOGGER.warn("Item shader config {} in {} is not a JSON object",
                        temporaryFile ? "temporary file" : "file",
                        path.toAbsolutePath());
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            String sourceType = temporaryFile ? "temporary item shader config" : "item shader config";
            Strange.LOGGER.warn("Failed to read {} from {}", sourceType, path.toAbsolutePath(), exception);
            return null;
        }
    }

    private static void recoverTemporaryConfigIfNeeded() {
        Path configPath = CONFIG_FILE.toPath();
        Path tempPath = tempConfigPath();
        if (!CONFIG_FILE.exists() && Files.isRegularFile(tempPath)) {
            promoteRecoveredTempConfig(tempPath, configPath);
        }
    }

    private static void promoteRecoveredTempConfig(Path tempPath, Path configPath) {
        try {
            try {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to promote recovered item shader temp file {}", tempPath.toAbsolutePath(), exception);
        }
    }

    private static Path tempConfigPath() {
        return CONFIG_FILE.toPath().resolveSibling(CONFIG_FILE.getName() + ".tmp");
    }

    private static void logInvalidProfileValueOnce(String itemId, String key, JsonElement value, String expected, Exception exception) {
        String logKey = itemId + ":" + key;
        if (!LOGGED_INVALID_CONFIG_VALUES.add(logKey)) {
            return;
        }

        if (exception == null) {
            Strange.LOGGER.warn("Invalid item shader profile value {}.{}={}, expected {}",
                    itemId, key, value, expected);
        } else {
            Strange.LOGGER.warn("Invalid item shader profile value {}.{}={}, expected {}",
                    itemId, key, value, expected, exception);
        }
    }

    private static void logInvalidThemeNameOnce(String itemId, String themeName) {
        String logKey = itemId + ":theme";
        if (LOGGED_INVALID_CONFIG_VALUES.add(logKey)) {
            Strange.LOGGER.warn("Unknown item shader theme {} for {}", themeName, itemId);
        }
    }

    private static void logInvalidItemIdOnce(String rawItemId, Exception exception) {
        String logKey = rawItemId == null ? "<null>" : rawItemId;
        if (!LOGGED_INVALID_ITEM_IDS.add(logKey)) {
            return;
        }

        Strange.LOGGER.warn("Invalid item shader profile id {}", rawItemId, exception);
    }

    private static void logDuplicateItemIdOnce(String normalizedId, String rawItemId) {
        if (LOGGED_DUPLICATE_ITEM_IDS.add(normalizedId)) {
            Strange.LOGGER.warn("Duplicate normalized item shader profile id {} from key {}", normalizedId, rawItemId);
        }
    }
}
