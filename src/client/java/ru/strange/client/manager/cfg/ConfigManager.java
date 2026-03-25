package ru.strange.client.manager.cfg;

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

public final class ConfigManager extends Manager<Config> {
    private static final int MAX_CONFIG_NAME_LENGTH = 64;
    private static final String CONFIG_NAME_PATTERN = "^[A-Za-z0-9_-]{1," + MAX_CONFIG_NAME_LENGTH + "}$";
    private static final long AUTO_SAVE_DELAY_MS = 750L;

    public static final File configDirectory = new File(Strange.root, "configs" + File.separator + "cfg");

    private final ArrayList<Config> loadedConfigs = new ArrayList<>();
    private int autoSaveSuppressionDepth;
    private boolean dirty;
    private long nextAutoSaveAt = Long.MIN_VALUE;

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
    }

    public ArrayList<Config> getLoadedConfigs() {
        return new ArrayList<>(loadedConfigs);
    }

    public void load() {
        loadedConfigs.clear();
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

        if (!config.getFile().isFile()) {
            return false;
        }

        try (Reader reader = Files.newBufferedReader(config.getFile().toPath(), StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            boolean[] sanitizedReservedBinds = new boolean[1];
            withAutoSaveSuppressed(() -> {
                config.load(object);
                sanitizedReservedBinds[0] = KeyBindPolicy.sanitizeAllCustomBinds();
            });
            if (sanitizedReservedBinds[0] && !saveConfig(normalizedName)) {
                Strange.LOGGER.warn("Failed to persist sanitized reserved binds for config {}", normalizedName);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Strange.LOGGER.warn("Failed to load config {}", normalizedName, e);
            return false;
        }
    }

    public boolean saveConfig(String configName) {
        String normalizedName = normalizeConfigName(configName);
        if (normalizedName == null) {
            return false;
        }

        Config config = findConfig(normalizedName);
        if (config == null) {
            config = new Config(normalizedName);
            registerConfig(config);
        }

        String contentPrettyPrint = new GsonBuilder().setPrettyPrinting().create().toJson(config.save());
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
        if (file.exists()) {
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

        File file = config.getFile();
        if (!file.exists()) {
            loadedConfigs.remove(config);
            return false;
        }

        if (!file.delete()) {
            Strange.LOGGER.warn("Failed to delete config file {}", file.getAbsolutePath());
            return false;
        }

        loadedConfigs.remove(config);
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
        if (!savePendingDefaultConfig()) {
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

        if (!savePendingDefaultConfig()) {
            nextAutoSaveAt = System.currentTimeMillis() + AUTO_SAVE_DELAY_MS;
        }
    }

    public String normalizeConfigName(String configName) {
        if (configName == null) {
            return null;
        }

        String normalized = configName.trim();
        if (normalized.isEmpty() || !normalized.matches(CONFIG_NAME_PATTERN)) {
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

    private void scheduleAutoSave() {
        dirty = true;
        nextAutoSaveAt = System.currentTimeMillis() + AUTO_SAVE_DELAY_MS;
    }

    private boolean savePendingDefaultConfig() {
        return saveConfig(Strange.DEFAULT_CONFIG_NAME);
    }

    private void clearPendingAutoSave() {
        dirty = false;
        nextAutoSaveAt = Long.MIN_VALUE;
    }
}
