package ru.strange.client.utils.other;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.strange.client.Strange;
import ru.strange.client.module.api.Module;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ServerRulesConfig {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final long RELOAD_CHECK_INTERVAL_MS = 1000L;
    private static final File CONFIG_DIRECTORY = new File(Strange.root, "configs");
    private static final File CONFIG_FILE = new File(CONFIG_DIRECTORY, "server-rules.json");

    private static List<ServerRuleProfile> profiles = List.of();
    private static boolean initialized;
    private static long lastModified = Long.MIN_VALUE;
    private static long lastReloadCheckAt;

    private ServerRulesConfig() {
    }

    public static synchronized void ensureLoaded() {
        if (initialized) {
            return;
        }

        initialized = true;
        loadOrCreate();
    }

    public static synchronized boolean reloadIfModified() {
        ensureLoaded();

        long now = System.currentTimeMillis();
        if ((now - lastReloadCheckAt) < RELOAD_CHECK_INTERVAL_MS) {
            return false;
        }
        lastReloadCheckAt = now;

        if (!CONFIG_FILE.exists()) {
            loadOrCreate();
            return true;
        }

        long currentModified = CONFIG_FILE.lastModified();
        if (currentModified != lastModified) {
            loadOrCreate();
            return true;
        }

        return false;
    }

    public static synchronized ServerRuleProfile findMatchingProfile(ServerUtil.ServerContext context) {
        ensureLoaded();
        if (context == null) {
            return null;
        }

        for (ServerRuleProfile profile : profiles) {
            if (profile.matches(context)) {
                return profile;
            }
        }

        return null;
    }

    public static File getConfigFile() {
        return CONFIG_FILE;
    }

    private static void loadOrCreate() {
        try {
            Files.createDirectories(CONFIG_DIRECTORY.toPath());
            if (!CONFIG_FILE.exists()) {
                writeDefaultConfig();
            }

            loadFromDisk();
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to prepare server rules config at {}", CONFIG_FILE.getAbsolutePath(), e);
            if (profiles.isEmpty()) {
                profiles = List.of(defaultProfile());
            }
            lastModified = CONFIG_FILE.exists() ? CONFIG_FILE.lastModified() : Long.MIN_VALUE;
        }
    }

    private static void writeDefaultConfig() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);

        JsonArray profilesArray = new JsonArray();
        profilesArray.add(defaultProfile().toJson());
        root.add("profiles", profilesArray);

        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static void loadFromDisk() {
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            JsonObject root = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            ProfileParseResult parseResult = parseProfiles(root);
            if (parseResult.malformed() && parseResult.profiles().isEmpty()) {
                Strange.LOGGER.warn("Server rules config {} is malformed, keeping previous profiles", CONFIG_FILE.getAbsolutePath());
                profiles = fallbackProfiles();
            } else {
                if (parseResult.malformed()) {
                    Strange.LOGGER.warn("Server rules config {} contains invalid profiles, only valid entries were loaded", CONFIG_FILE.getAbsolutePath());
                }
                profiles = List.copyOf(parseResult.profiles());
            }
            lastModified = CONFIG_FILE.lastModified();
        } catch (IOException | RuntimeException e) {
            Strange.LOGGER.warn("Failed to load server rules config {}", CONFIG_FILE.getAbsolutePath(), e);
            profiles = fallbackProfiles();
            lastModified = CONFIG_FILE.exists() ? CONFIG_FILE.lastModified() : Long.MIN_VALUE;
        }
    }

    private static ProfileParseResult parseProfiles(JsonObject root) {
        if (root == null || !root.has("profiles") || !root.get("profiles").isJsonArray()) {
            return new ProfileParseResult(List.of(), true);
        }

        JsonArray profilesArray = root.getAsJsonArray("profiles");
        List<ServerRuleProfile> result = new ArrayList<>();
        boolean malformed = false;
        for (JsonElement element : profilesArray) {
            if (!element.isJsonObject()) {
                malformed = true;
                continue;
            }

            ServerRuleProfile profile = parseProfile(element.getAsJsonObject());
            if (profile != null) {
                result.add(profile);
            } else {
                malformed = true;
            }
        }

        if (profilesArray.isEmpty()) {
            return new ProfileParseResult(List.of(), false);
        }

        return new ProfileParseResult(List.copyOf(result), malformed);
    }

    private static ServerRuleProfile parseProfile(JsonObject object) {
        String name = readString(object, "name", "profile", "id");
        List<String> serverPatterns = readStringList(object, "servers", "match", "matches", "ips", "addresses");
        List<String> hiddenModules = readStringList(object, "hidden_modules", "hiddenModules", "modules", "hide");

        if (serverPatterns.isEmpty()) {
            return null;
        }

        if (name == null || name.isBlank()) {
            name = serverPatterns.getFirst();
        }

        return new ServerRuleProfile(name, serverPatterns, hiddenModules);
    }

    private static String readString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }

            String value = object.get(key).getAsString();
            if (!value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private static List<String> readStringList(JsonObject object, String... keys) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String key : keys) {
            if (!object.has(key)) {
                continue;
            }

            JsonElement element = object.get(key);
            if (element.isJsonArray()) {
                for (JsonElement arrayElement : element.getAsJsonArray()) {
                    if (!arrayElement.isJsonPrimitive()) {
                        continue;
                    }

                    String value = arrayElement.getAsString().trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            } else if (element.isJsonPrimitive()) {
                String value = element.getAsString().trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }

        return new ArrayList<>(values);
    }

    private static ServerRuleProfile defaultProfile() {
        return new ServerRuleProfile(
                "HolyWorld",
                List.of(
                        "holyworld",
                        "holy-world",
                        "*.holyworld*"
                ),
                List.of(
                        "AutoSwap",
                        "FreeLook"
                )
        );
    }

    private static List<ServerRuleProfile> fallbackProfiles() {
        return profiles.isEmpty() ? List.of(defaultProfile()) : profiles;
    }

    private record ProfileParseResult(List<ServerRuleProfile> profiles, boolean malformed) {
    }

    public static final class ServerRuleProfile {
        private final String name;
        private final List<String> serverPatterns;
        private final List<String> hiddenModuleIds;
        private final Set<String> normalizedHiddenModuleIds;

        public ServerRuleProfile(String name, List<String> serverPatterns, List<String> hiddenModuleIds) {
            this.name = name == null || name.isBlank() ? "Unnamed profile" : name.trim();
            this.serverPatterns = copyNormalizedList(serverPatterns, false);
            this.hiddenModuleIds = copyNormalizedList(hiddenModuleIds, true);
            this.normalizedHiddenModuleIds = normalizeModuleIds(this.hiddenModuleIds);
        }

        public String getName() {
            return name;
        }

        public List<String> getHiddenModuleIds() {
            return hiddenModuleIds;
        }

        public boolean matches(ServerUtil.ServerContext context) {
            if (context == null || serverPatterns.isEmpty()) {
                return false;
            }

            List<String> targets = List.of(
                    context.id(),
                    context.name(),
                    context.address(),
                    context.combined()
            );

            for (String pattern : serverPatterns) {
                if (pattern.isEmpty()) {
                    continue;
                }

                for (String target : targets) {
                    if (matchesPattern(target, pattern)) {
                        return true;
                    }
                }
            }

            return false;
        }

        public boolean matchesModule(Module module) {
            if (module == null || normalizedHiddenModuleIds.isEmpty()) {
                return false;
            }

            return normalizedHiddenModuleIds.contains(normalizeModuleId(module.name))
                    || normalizedHiddenModuleIds.contains(normalizeModuleId(module.getClass().getSimpleName()))
                    || normalizedHiddenModuleIds.contains(normalizeModuleId(module.getClass().getName()));
        }

        public int countMatchingModules(Iterable<Module> modules) {
            int count = 0;
            if (modules == null) {
                return 0;
            }

            for (Module module : modules) {
                if (matchesModule(module)) {
                    count++;
                }
            }

            return count;
        }

        public JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("name", name);

            JsonArray serversArray = new JsonArray();
            for (String serverPattern : serverPatterns) {
                serversArray.add(serverPattern);
            }
            object.add("servers", serversArray);

            JsonArray modulesArray = new JsonArray();
            for (String moduleId : hiddenModuleIds) {
                modulesArray.add(moduleId);
            }
            object.add("hidden_modules", modulesArray);
            return object;
        }

        private static boolean matchesPattern(String target, String pattern) {
            if (target == null || target.isEmpty()) {
                return false;
            }

            if (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0) {
                return globMatches(target, pattern);
            }

            return target.equals(pattern) || containsDelimitedToken(target, pattern);
        }

        private static boolean globMatches(String target, String pattern) {
            StringBuilder regex = new StringBuilder(pattern.length() * 2);
            for (int i = 0; i < pattern.length(); i++) {
                char current = pattern.charAt(i);
                switch (current) {
                    case '*' -> regex.append(".*");
                    case '?' -> regex.append('.');
                    case '\\', '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|' -> regex.append('\\').append(current);
                    default -> regex.append(current);
                }
            }
            return target.matches(regex.toString());
        }

        private static boolean containsDelimitedToken(String target, String token) {
            if (token == null || token.isEmpty()) {
                return false;
            }

            int fromIndex = 0;
            while (fromIndex < target.length()) {
                int matchIndex = target.indexOf(token, fromIndex);
                if (matchIndex < 0) {
                    return false;
                }

                int endIndex = matchIndex + token.length();
                if (isBoundary(target, matchIndex - 1) && isBoundary(target, endIndex)) {
                    return true;
                }

                fromIndex = matchIndex + 1;
            }

            return false;
        }

        private static boolean isBoundary(String value, int index) {
            if (index < 0 || index >= value.length()) {
                return true;
            }

            return !Character.isLetterOrDigit(value.charAt(index));
        }

        private static List<String> copyNormalizedList(List<String> values, boolean preserveCase) {
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            if (values == null) {
                return List.of();
            }

            for (String value : values) {
                if (value == null) {
                    continue;
                }

                String normalized = preserveCase ? value.trim() : ServerUtil.normalizeServerToken(value);
                if (!normalized.isEmpty()) {
                    copy.add(normalized);
                }
            }

            return List.copyOf(copy);
        }

        private static Set<String> normalizeModuleIds(List<String> values) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (String value : values) {
                String normalized = normalizeModuleId(value);
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            }
            return Set.copyOf(result);
        }

        private static String normalizeModuleId(String value) {
            if (value == null) {
                return "";
            }

            String lowerCase = value.toLowerCase(Locale.ROOT);
            StringBuilder normalized = new StringBuilder(lowerCase.length());
            for (int i = 0; i < lowerCase.length(); i++) {
                char current = lowerCase.charAt(i);
                if (Character.isLetterOrDigit(current)) {
                    normalized.append(current);
                }
            }
            return normalized.toString();
        }
    }
}
