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
import java.util.ArrayList;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemShaderProfiles {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();
    private static final long RELOAD_CHECK_INTERVAL_MS = 1000L;
    private static final File CONFIG_FILE = new File(Strange.root, "item-shaders.json");

    private static boolean initialized;
    private static boolean enabled = true;
    private static long lastModified = Long.MIN_VALUE;
    private static long lastReloadCheckAt;
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
        return itemId == null ? null : profiles.get(itemId.toString());
    }

    public static synchronized ShaderProfile find(String itemId) {
        ensureLoaded();

        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        return profiles.get(itemId.trim());
    }

    public static synchronized Map<String, ShaderProfile> snapshot() {
        ensureLoaded();
        return new LinkedHashMap<>(profiles);
    }

    public static synchronized void tick() {
        ensureLoaded();
        reloadIfModified();
    }

    public static synchronized void setTheme(String itemId, ShaderThemePreset theme) {
        if (itemId == null || itemId.isBlank() || theme == null) {
            return;
        }

        ensureLoaded();
        reloadIfModified();

        String normalizedId = itemId.trim();
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
        saveCurrentConfig();
    }

    public static synchronized void clear(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }

        ensureLoaded();
        reloadIfModified();

        Map<String, ShaderProfile> mutable = new LinkedHashMap<>(profiles);
        if (mutable.remove(itemId.trim()) != null) {
            profiles = Map.copyOf(mutable);
            saveCurrentConfig();
        }
    }

    public static File getConfigFile() {
        return CONFIG_FILE;
    }

    private static void reloadIfModified() {
        long now = System.currentTimeMillis();
        if ((now - lastReloadCheckAt) < RELOAD_CHECK_INTERVAL_MS) {
            return;
        }
        lastReloadCheckAt = now;

        if (!CONFIG_FILE.exists()) {
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
            Files.createDirectories(Strange.root.toPath());
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
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            JsonObject root = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();

            enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            profiles = parseProfiles(root);
            lastModified = CONFIG_FILE.lastModified();
        } catch (IOException | RuntimeException e) {
            Strange.LOGGER.warn("Failed to load item shader profiles {}", CONFIG_FILE.getAbsolutePath(), e);
            enabled = true;
            profiles = Map.of();
            lastModified = CONFIG_FILE.exists() ? CONFIG_FILE.lastModified() : Long.MIN_VALUE;
        }
    }

    private static void saveCurrentConfig() {
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

            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            lastModified = CONFIG_FILE.lastModified();
            lastReloadCheckAt = System.currentTimeMillis();
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to save item shader profiles {}", CONFIG_FILE.getAbsolutePath(), e);
        }
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

            String itemId = entry.getKey();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }

            ShaderProfile profile = parseProfile(itemId.trim(), entry.getValue().getAsJsonObject());
            if (profile != null) {
                result.put(itemId.trim(), profile);
            }
        }

        return Map.copyOf(result);
    }

    private static ShaderProfile parseProfile(String itemId, JsonObject object) {
        String theme = readString(object, "theme", "preset", "shader");
        Integer tintColor = readColor(object, "tintColor", "tint", "color");
        Float tintMix = readFloat(object, "tintMix", "mix");
        Float pulseAlpha = readFloat(object, "pulseAlpha", "alpha");
        Boolean pulseEffectOnly = readBoolean(object, "pulseEffectOnly", "effectOnly");

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

    private static Float readFloat(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }

            try {
                return object.get(key).getAsFloat();
            } catch (RuntimeException ignored) {
            }
        }

        return null;
    }

    private static Boolean readBoolean(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }

            try {
                return object.get(key).getAsBoolean();
            } catch (RuntimeException ignored) {
            }
        }

        return null;
    }

    private static Integer readColor(JsonObject object, String... keys) {
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
            } catch (RuntimeException ignored) {
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
}
