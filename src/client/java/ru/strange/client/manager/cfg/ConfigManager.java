package ru.strange.client.manager.cfg;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FilenameUtils;
import ru.strange.client.Strange;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public final class ConfigManager extends Manager<Config> {
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");
    
    public static final File configDirectory = getConfigDirectory();
    private static final ArrayList<Config> loadedConfigs = new ArrayList<>();
    
    private static File getConfigDirectory() {
        if (IS_LINUX) {
            String homeDir = System.getProperty("user.home");
            return new File(homeDir, ".strangevisual" + File.separator + "configs" + File.separator + "cfg");
        } else {
            return new File(Strange.root, "configs" + File.separator + "cfg");
        }
    }

    public ConfigManager() {
        setContents(loadConfigs());
        configDirectory.mkdirs();
    }

    private static ArrayList<Config> loadConfigs() {
        File[] files = configDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (FilenameUtils.getExtension(file.getName()).equals("json"))
                    loadedConfigs.add(new Config(FilenameUtils.removeExtension(file.getName())));
            }
        }
        return loadedConfigs;
    }

    public static ArrayList<Config> getLoadedConfigs() {
        return loadedConfigs;
    }

    public void load() {
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }
        if (configDirectory != null) {
            File[] files = configDirectory.listFiles(f -> !f.isDirectory() && FilenameUtils.getExtension(f.getName()).equals("json"));
            for (File f : files) {
                Config config = new Config(FilenameUtils.removeExtension(f.getName()).replace(" ", ""));
                loadedConfigs.add(config);
            }
        }
    }

    public boolean loadConfig(String configName) {
        if (configName == null)
            return false;
        Config config = findConfig(configName);
        if (config == null)
            return false;
        try (FileReader reader = new FileReader(config.getFile())) {
            JsonParser parser = new JsonParser();
            JsonObject object = (JsonObject) parser.parse(reader);
            config.load(object);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean saveConfig(String configName) {

        if (configName == null)
            return false;
        Config config;
        if ((config = findConfig(configName)) == null) {
            Config newConfig = (config = new Config(configName));
            getContents().add(newConfig);
        }

        String contentPrettyPrint = new GsonBuilder().setPrettyPrinting().create().toJson(config.save());
        try (FileWriter writer = new FileWriter(config.getFile())) {
            writer.write(contentPrettyPrint);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public Config findConfig(String configName) {
        if (configName == null) return null;
        for (Config config : getContents()) {
            if (config.getName().equalsIgnoreCase(configName))
                return config;
        }

        if (new File(configDirectory, configName + ".json").exists())
            return new Config(configName);

        return null;
    }

    public boolean deleteConfig(String configName) {
        if (configName == null)
            return false;
        Config config;
        if ((config = findConfig(configName)) != null) {
            final File f = config.getFile();
            getContents().remove(config);
            return f.exists() && f.delete();
        }
        return false;
    }

    /**
     * Автоматически сохраняет конфиг "default" при изменении модулей или настроек
     */
    public void autoSave() {
        if (Strange.get.configManager == null) {
            return;
        }
        // Сохраняем в конфиг "default"
        saveConfig("default");
    }
}