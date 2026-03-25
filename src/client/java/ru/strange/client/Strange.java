package ru.strange.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.strange.client.command.CommandManager;
import ru.strange.client.event.EventManager;
import ru.strange.client.manager.cfg.ConfigManager;
import ru.strange.client.manager.friend.FriendManager;
import ru.strange.client.module.api.Manager;
import ru.strange.client.renderengine.font.FontManager;
import ru.strange.client.renderengine.renderers.pipeline.PipelineInitializer;
import ru.strange.client.rpc.RPC;
import ru.strange.client.utils.combat.AttackTracker;
import ru.strange.client.utils.combat.CombatStateTracker;
import ru.strange.client.utils.other.ItemShaderProfiles;
import ru.strange.client.utils.other.MinecraftLogFilter;
import ru.strange.client.utils.other.ServerRestrictionManager;

public class Strange implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("StrangeVisuals");
    public static final String MOD_METADATA_ID = "strange-visuals";
    public static final String ASSET_NAMESPACE = "strange";
    public static final String DEFAULT_CONFIG_NAME = "default";
    private static final String STORAGE_DIR_NAME = "strangevisual";

    public static Strange get;

    public static String name = "Strange Visuals";
    public static final File root = resolveRootDirectory();
    public static String rootRes = ASSET_NAMESPACE;
    public static final String version = resolveVersion();

    public Manager manager;
    public ConfigManager configManager;
    public FriendManager friendManager;
    public CommandManager commandManager;
    private final RPC rpc = new RPC();

    public static Identifier id(String path) {
        return Identifier.of(rootRes, normalizeAssetPath(path));
    }

    public static String normalizeAssetPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Asset path cannot be blank");
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static File resolveRootDirectory() {
        File configRoot = FabricLoader.getInstance().getConfigDir().toFile();
        File targetRoot = new File(configRoot, STORAGE_DIR_NAME);
        File legacyRoot = new File(configRoot, name);
        File legacyLinuxRoot = new File(System.getProperty("user.home"), ".strangevisuals");
        File migratedLinuxRoot = new File(System.getProperty("user.home"), ".strangevisual");

        migrateDirectory(legacyRoot, targetRoot);
        migrateDirectory(legacyLinuxRoot, targetRoot);
        migrateDirectory(migratedLinuxRoot, targetRoot);

        if (!targetRoot.exists() && !targetRoot.mkdirs()) {
            LOGGER.warn("Failed to create storage directory {}", targetRoot.getAbsolutePath());
        }
        File hitSoundRoot = new File(targetRoot, "hitsounds");
        if (!hitSoundRoot.exists() && !hitSoundRoot.mkdirs()) {
            LOGGER.warn("Failed to create custom hit sound directory {}", hitSoundRoot.getAbsolutePath());
        }
        return targetRoot;
    }

    private static String resolveVersion() {
        String metadataVersion = FabricLoader.getInstance()
                .getModContainer(MOD_METADATA_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
        if (isResolvedVersion(metadataVersion)) {
            return metadataVersion;
        }

        String resourceVersion = readVersionResource();
        if (isResolvedVersion(resourceVersion)) {
            return resourceVersion;
        }

        return "dev";
    }

    private static boolean isResolvedVersion(String value) {
        return value != null
                && !value.isBlank()
                && !value.contains("${")
                && !value.contains("}");
    }

    private static String readVersionResource() {
        try (InputStream stream = Strange.class.getResourceAsStream("/strange.version")) {
            if (stream == null) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String value = reader.readLine();
                return value == null ? null : value.trim();
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to read strange.version resource", exception);
            return null;
        }
    }

    private static void migrateDirectory(File source, File target) {
        if (source == null || target == null || !source.exists() || target.exists()) {
            return;
        }

        try {
            if (!source.renameTo(target)) {
                LOGGER.warn("Failed to migrate storage directory from {} to {}", source.getAbsolutePath(), target.getAbsolutePath());
            }
        } catch (SecurityException exception) {
            LOGGER.warn("Failed to migrate storage directory from {} to {}", source.getAbsolutePath(), target.getAbsolutePath(), exception);
        }
    }

    public static String getDisplayVersion() {
        return version.startsWith("v") ? version : "v" + version;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing {} {}", name, version);
        get = this;
        manager = new Manager();
        configManager = new ConfigManager();
        friendManager = new FriendManager();
        commandManager = new CommandManager();
        MinecraftLogFilter.install();
        ServerRestrictionManager.initialize();
        ItemShaderProfiles.ensureLoaded();

        PipelineInitializer.init();

        FontManager fontManager = FontManager.getInstance();

        try {
            fontManager.loadFontFromResources("medium", "strange:fonts/medium.ttf");
        } catch (Exception e) {
            LOGGER.error("Failed to load medium font", e);
        }

        rpc.startRpc();
        Runtime.getRuntime().addShutdownHook(new Thread(rpc::shutdownRpc, "strange-rpc-shutdown"));

        if (configManager.findConfig(DEFAULT_CONFIG_NAME) != null && configManager.loadConfig(DEFAULT_CONFIG_NAME)) {
            LOGGER.info("Loaded default config");
        }

        EventManager.register(this);
        EventManager.register(AttackTracker.getInstance());
        EventManager.register(CombatStateTracker.getInstance());
    }
}
