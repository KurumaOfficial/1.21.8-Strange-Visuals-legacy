package ru.strange.client.manager.cfg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FilenameUtils;
import ru.strange.client.Strange;
import ru.strange.client.utils.other.KeyBindPolicy;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class ConfigManager extends Manager<Config> {
    private static final int MAX_CONFIG_NAME_LENGTH = 96;
    private static final String INVALID_CONFIG_CHARS = "<>:\"/\\\\|?*";
    private static final long AUTO_SAVE_DELAY_MS = 750L;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> RESERVED_WINDOWS_NAMES = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    public static final File configDirectory = new File(Strange.root, "configs" + File.separator + "cfg");

    private final ArrayList<Config> loadedConfigs = new ArrayList<>();
    private int autoSaveSuppressionDepth;
    private boolean dirty;
    private long nextAutoSaveAt = Long.MIN_VALUE;
    private String activeConfigName = Strange.DEFAULT_CONFIG_NAME;
    private String pendingAutoSaveConfigName = Strange.DEFAULT_CONFIG_NAME;

    public ConfigManager() {
        setContents(loadedConfigs);
        load();
    }

    private File[] listConfigFiles() {
        if (!configDirectory.exists() && !configDirectory.mkdirs()) {
            Strange.LOGGER.warn("Failed to create config directory at {}", configDirectory.getAbsolutePath());
        }

        File[] files = configDirectory.listFiles(file ->
                !file.isDirectory() && FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("json"));
        if (files == null) {
            return new File[0];
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    private void registerConfig(Config config) {
        for (Config loadedConfig : loadedConfigs) {
            if (loadedConfig.getName().equalsIgnoreCase(config.getName())) {
                return;
            }
        }
        loadedConfigs.add(config);
        sortLoadedConfigs();
    }

    private Config findLoadedConfig(String configName) {
        for (Config loadedConfig : loadedConfigs) {
            if (loadedConfig.getName().equalsIgnoreCase(configName)) {
                return loadedConfig;
            }
        }
        return null;
    }

    public ArrayList<Config> getLoadedConfigs() {
        ArrayList<Config> configs = new ArrayList<>(loadedConfigs);
        configs.sort(Comparator.comparing(Config::getName, String.CASE_INSENSITIVE_ORDER));
        return configs;
    }

    public void load() {
        loadedConfigs.clear();
        recoverTemporaryConfigs();
        for (File file : listConfigFiles()) {
            String name = normalizeConfigName(FilenameUtils.removeExtension(file.getName()));
            if (name == null) {
                Strange.LOGGER.warn("Skipping config with invalid file name {}", file.getAbsolutePath());
                continue;
            }
            registerConfig(new Config(name));
        }
    }

    public boolean loadConfig(String configName) {
        String normalizedName = normalizeConfigName(configName);
        if (normalizedName == null) {
            return false;
        }

        Config config = findConfig(normalizedName);
        if (config == null) {
            return false;
        }

        if (!config.getFile().isFile() && !Files.isRegularFile(tempConfigPath(config.getFile().toPath()))) {
            return false;
        }

        JsonObject object = loadConfigObject(config, normalizedName);
        if (object == null) {
            return false;
        }

        JsonObject previousState = captureRuntimeSnapshot(normalizedName);
        if (previousState == null) {
            return false;
        }
        boolean[] sanitizedReservedBinds = new boolean[1];
        try {
            withAutoSaveSuppressed(() -> {
                config.load(object);
                sanitizedReservedBinds[0] = KeyBindPolicy.sanitizeAllCustomBinds();
            });
        } catch (RuntimeException e) {
            rollbackLoadedState(previousState, normalizedName, e);
            return false;
        }

        setActiveConfigName(normalizedName);
        clearPendingAutoSave();
        if (sanitizedReservedBinds[0] && !saveConfig(normalizedName)) {
            Strange.LOGGER.warn("Failed to persist sanitized reserved binds for config {}", normalizedName);
        }
        return true;
    }

    public boolean saveConfig(String configName) {
        String normalizedName = normalizeConfigName(configName);
        if (normalizedName == null) {
            return false;
        }

        Config config = findLoadedConfig(normalizedName);
        boolean alreadyRegistered = config != null;
        if (config == null) {
            config = new Config(normalizedName);
        }

        JsonObject configObject = tryBuildConfigObject(config, normalizedName);
        if (configObject == null) {
            return false;
        }

        String contentPrettyPrint = PRETTY_GSON.toJson(configObject);
        try {
            ensureConfigDirectory();
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to prepare config directory for {}", normalizedName, e);
            return false;
        }

        Path configPath = config.getFile().toPath();
        Path tempFile = configPath.resolveSibling(config.getFile().getName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            writer.write(contentPrettyPrint);
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to stage config {}", normalizedName, e);
            return false;
        }

        try {
            try {
                Files.move(tempFile, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!alreadyRegistered) {
                registerConfig(config);
            }
            setActiveConfigName(normalizedName);
            clearPendingAutoSave();
            return true;
        } catch (IOException e) {
            Strange.LOGGER.warn("Failed to save config {}", normalizedName, e);
            return false;
        }
    }

    public Config findConfig(String configName) {
        String normalizedName = normalizeConfigName(configName);
        if (normalizedName == null) {
            return null;
        }

        for (Config config : loadedConfigs) {
            if (config.getName().equalsIgnoreCase(normalizedName)) {
                return config;
            }
        }

        File file = resolveConfigFile(normalizedName);
        if (file.exists() || Files.isRegularFile(tempConfigPath(file.toPath()))) {
            Config config = new Config(normalizedName);
            registerConfig(config);
            return config;
        }

        return null;
    }

    public boolean deleteConfig(String configName) {
        String normalizedName = normalizeConfigName(configName);
        if (normalizedName == null) {
            return false;
        }

        Config config = findConfig(normalizedName);
        if (config == null) {
            return false;
        }

        Path configPath = config.getFile().toPath();
        Path tempPath = tempConfigPath(configPath);
        boolean hadConfigFile = Files.exists(configPath);
        boolean hadTempFile = Files.exists(tempPath);

        if (hadConfigFile && !deleteConfigFile(configPath, normalizedName, false)) {
            return false;
        }
        if (hadTempFile && !deleteConfigFile(tempPath, normalizedName, true)) {
            return false;
        }

        boolean removedLoadedConfig = loadedConfigs.remove(config);
        if (!hadConfigFile && !hadTempFile && !removedLoadedConfig) {
            return false;
        }

        if (normalizedName.equalsIgnoreCase(activeConfigName)) {
            setActiveConfigName(Strange.DEFAULT_CONFIG_NAME);
            if (dirty) {
                pendingAutoSaveConfigName = activeConfigName;
            }
        }
        return true;
    }

    public void withAutoSaveSuppressed(Runnable action) {
        autoSaveSuppressionDepth++;
        try {
            action.run();
        } finally {
            autoSaveSuppressionDepth--;
        }
    }

    public boolean isAutoSaveSuppressed() {
        return autoSaveSuppressionDepth > 0;
    }

    public void markDirty() {
        if (isAutoSaveSuppressed()) {
            return;
        }
        scheduleAutoSave();
    }

    public void flushAutoSave() {
        if (!dirty || isAutoSaveSuppressed()) {
            return;
        }
        if (!savePendingActiveConfig()) {
            nextAutoSaveAt = System.currentTimeMillis() + AUTO_SAVE_DELAY_MS;
        }
    }

    public void autoSave() {
        if (isAutoSaveSuppressed()) {
            return;
        }
        scheduleAutoSave();
    }

    public void tickAutoSave() {
        if (!dirty || isAutoSaveSuppressed() || nextAutoSaveAt == Long.MIN_VALUE) {
            return;
        }

        if (System.currentTimeMillis() < nextAutoSaveAt) {
            return;
        }

        if (!savePendingActiveConfig()) {
            nextAutoSaveAt = System.currentTimeMillis() + AUTO_SAVE_DELAY_MS;
        }
    }

    public String getActiveConfigName() {
        return activeConfigName;
    }

    public String normalizeConfigName(String configName) {
        if (configName == null) {
            return null;
        }

        String normalized = configName.trim();
        if (normalized.isEmpty()
                || normalized.length() > MAX_CONFIG_NAME_LENGTH
                || ".".equals(normalized)
                || "..".equals(normalized)
                || normalized.endsWith(".")) {
            return null;
        }

        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (Character.isISOControl(current) || INVALID_CONFIG_CHARS.indexOf(current) >= 0) {
                return null;
            }
        }

        if (normalized.isBlank()) {
            return null;
        }

        if (RESERVED_WINDOWS_NAMES.contains(normalized.toLowerCase(Locale.ROOT))) {
            return null;
        }

        return normalized;
    }

    public boolean isValidConfigName(String configName) {
        return normalizeConfigName(configName) != null;
    }

    public File resolveConfigFile(String configName) {
        String normalizedName = normalizeConfigName(configName);
        if (normalizedName == null) {
            throw new IllegalArgumentException("Invalid config name");
        }

        Path configRoot = configDirectory.toPath().toAbsolutePath().normalize();
        Path filePath = configRoot.resolve(normalizedName + ".json").normalize();
        if (!filePath.startsWith(configRoot)) {
            throw new IllegalArgumentException("Config path escapes storage root");
        }
        return filePath.toFile();
    }

    private void ensureConfigDirectory() throws IOException {
        Files.createDirectories(configDirectory.toPath());
    }

    private void sortLoadedConfigs() {
        loadedConfigs.sort(Comparator.comparing(Config::getName, String.CASE_INSENSITIVE_ORDER));
    }

    private void scheduleAutoSave() {
        dirty = true;
        pendingAutoSaveConfigName = resolveAutoSaveConfigName();
        nextAutoSaveAt = System.currentTimeMillis() + AUTO_SAVE_DELAY_MS;
    }

    private boolean savePendingActiveConfig() {
        return saveConfig(resolvePendingAutoSaveConfigName());
    }

    private void clearPendingAutoSave() {
        dirty = false;
        nextAutoSaveAt = Long.MIN_VALUE;
        pendingAutoSaveConfigName = resolveAutoSaveConfigName();
    }

    private void rollbackLoadedState(JsonObject previousState, String configName, RuntimeException cause) {
        try {
            withAutoSaveSuppressed(() -> new Config("__runtime_snapshot").load(previousState));
        } catch (RuntimeException rollbackException) {
            cause.addSuppressed(rollbackException);
            Strange.LOGGER.warn("Failed to roll back config state after load failure for {}", configName, rollbackException);
        }

        Strange.LOGGER.warn("Failed to load config {}", configName, cause);
    }

    private void setActiveConfigName(String configName) {
        String normalizedName = normalizeConfigName(configName);
        activeConfigName = normalizedName == null ? Strange.DEFAULT_CONFIG_NAME : normalizedName;
    }

    private String resolveAutoSaveConfigName() {
        return activeConfigName == null ? Strange.DEFAULT_CONFIG_NAME : activeConfigName;
    }

    private String resolvePendingAutoSaveConfigName() {
        String normalizedName = normalizeConfigName(pendingAutoSaveConfigName);
        return normalizedName == null ? resolveAutoSaveConfigName() : normalizedName;
    }

    private JsonObject captureRuntimeSnapshot(String configName) {
        return tryBuildConfigObject(new Config("__runtime_snapshot"), "__runtime_snapshot before loading " + configName);
    }

    private JsonObject tryBuildConfigObject(Config config, String configName) {
        try {
            return config.save();
        } catch (RuntimeException exception) {
            Strange.LOGGER.warn("Failed to serialize config state for {}", configName, exception);
            return null;
        }
    }

    private JsonObject loadConfigObject(Config config, String normalizedName) {
        Path configPath = config.getFile().toPath();
        JsonObject object = tryReadConfigObject(configPath, normalizedName, false);
        if (object != null) {
            return object;
        }

        Path tempPath = tempConfigPath(configPath);
        object = tryReadConfigObject(tempPath, normalizedName, true);
        if (object == null) {
            return null;
        }

        Strange.LOGGER.warn("Recovered config {} from temporary file {}", normalizedName, tempPath.toAbsolutePath());
        promoteRecoveredTempConfig(tempPath, configPath, normalizedName);
        return object;
    }

    private JsonObject tryReadConfigObject(Path path, String configName, boolean temporaryFile) {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            var parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                Strange.LOGGER.warn("Config {} in {} is not a JSON object", configName, path.toAbsolutePath());
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            String sourceType = temporaryFile ? "temporary config" : "config";
            Strange.LOGGER.warn("Failed to read {} {} from {}", sourceType, configName, path.toAbsolutePath(), exception);
            return null;
        }
    }

    private void recoverTemporaryConfigs() {
        try {
            ensureConfigDirectory();
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to prepare config directory for temporary config recovery", exception);
            return;
        }

        try (Stream<Path> paths = Files.list(configDirectory.toPath())) {
            paths.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json.tmp"))
                    .forEach(this::recoverTemporaryConfig);
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to inspect temporary config files in {}", configDirectory.getAbsolutePath(), exception);
        }
    }

    private void recoverTemporaryConfig(Path tempPath) {
        String fileName = tempPath.getFileName().toString();
        String baseFileName = fileName.substring(0, fileName.length() - ".tmp".length());
        if (!baseFileName.endsWith(".json")) {
            return;
        }

        Path configPath = tempPath.resolveSibling(baseFileName);
        if (Files.exists(configPath)) {
            return;
        }

        String normalizedName = normalizeConfigName(FilenameUtils.removeExtension(baseFileName));
        if (normalizedName == null) {
            Strange.LOGGER.warn("Skipping temporary config with invalid file name {}", tempPath.toAbsolutePath());
            return;
        }

        JsonObject recoveredObject = tryReadConfigObject(tempPath, normalizedName, true);
        if (recoveredObject == null) {
            Strange.LOGGER.warn("Skipping promotion of unreadable temporary config {}", tempPath.toAbsolutePath());
            return;
        }

        promoteRecoveredTempConfig(tempPath, configPath, normalizedName);
    }

    private void promoteRecoveredTempConfig(Path tempPath, Path configPath, String configName) {
        try {
            try {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to promote temporary config {} for {}", tempPath.toAbsolutePath(), configName, exception);
        }
    }

    private static Path tempConfigPath(Path configPath) {
        return configPath.resolveSibling(configPath.getFileName() + ".tmp");
    }

    private boolean deleteConfigFile(Path path, String configName, boolean temporaryFile) {
        try {
            Files.delete(path);
            return true;
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to delete {}config {} at {}",
                    temporaryFile ? "temporary " : "",
                    configName,
                    path.toAbsolutePath(),
                    exception);
            return false;
        }
    }
}
