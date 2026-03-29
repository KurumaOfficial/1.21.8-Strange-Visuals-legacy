package ru.strange.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import ru.strange.client.utils.other.ServerRestrictionManager;

public class Strange implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("StrangeVisuals");
    public static final String MOD_METADATA_ID = "strange-visuals";
    public static final String ASSET_NAMESPACE = "strange";
    public static final String DEFAULT_CONFIG_NAME = "default";
    private static final String STORAGE_DIR_NAME = "strangevisual";

    static {
        configureRuntimeEncoding();
    }

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

    private static void configureRuntimeEncoding() {
        String utf8 = StandardCharsets.UTF_8.name();
        System.setProperty("file.encoding", utf8);
        System.setProperty("sun.stdout.encoding", utf8);
        System.setProperty("sun.stderr.encoding", utf8);
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

        if (!targetRoot.exists() && !targetRoot.mkdirs()) {
            LOGGER.warn("Failed to create storage directory {}", targetRoot.getAbsolutePath());
        }
        migrateLegacyDirectories(configRoot, targetRoot);

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

    private static void migrateLegacyDirectories(File configRoot, File targetRoot) {
        for (File legacyRoot : resolveLegacyRoots(configRoot, targetRoot)) {
            migrateDirectory(legacyRoot, targetRoot);
        }
    }

    private static List<File> resolveLegacyRoots(File configRoot, File targetRoot) {
        List<File> roots = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();
        Path targetPath = normalizePath(targetRoot);
        if (targetPath != null) {
            seen.add(targetPath);
        }

        addLegacyRoot(roots, seen, new File(configRoot, name));
        addLegacyRoot(roots, seen, new File(System.getProperty("user.home"), ".strangevisuals"));
        addLegacyRoot(roots, seen, new File(System.getProperty("user.home"), ".strangevisual"));
        addLegacyRoot(roots, seen, new File("C:\\", name));

        return roots;
    }

    private static void addLegacyRoot(List<File> roots, Set<Path> seen, File candidate) {
        if (candidate == null) {
            return;
        }

        Path normalized = normalizePath(candidate);
        if (normalized == null || !seen.add(normalized)) {
            return;
        }

        roots.add(candidate);
    }

    private static void migrateDirectory(File source, File target) {
        if (source == null || target == null || !source.exists()) {
            return;
        }

        Path sourcePath = normalizePath(source);
        Path targetPath = normalizePath(target);
        if (sourcePath == null || targetPath == null || sourcePath.equals(targetPath)) {
            return;
        }

        try {
            if (!Files.exists(targetPath)) {
                Path parent = targetPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                try {
                    Files.move(sourcePath, targetPath);
                    LOGGER.info("Migrated storage directory from {} to {}", sourcePath, targetPath);
                    return;
                } catch (IOException moveException) {
                    LOGGER.debug("Direct storage move from {} to {} failed, falling back to merge", sourcePath, targetPath, moveException);
                }
            }

            int importedFiles = mergeDirectory(sourcePath, targetPath);
            if (importedFiles > 0) {
                LOGGER.info("Imported {} legacy storage files from {} into {}", importedFiles, sourcePath, targetPath);
            }
        } catch (IOException | SecurityException exception) {
            LOGGER.warn("Failed to migrate storage directory from {} to {}", source.getAbsolutePath(), target.getAbsolutePath(), exception);
        }
    }

    private static int mergeDirectory(Path sourceRoot, Path targetRoot) throws IOException {
        if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
            return 0;
        }

        Files.createDirectories(targetRoot);

        int[] imported = new int[1];
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceRoot.relativize(dir);
                Files.createDirectories(targetRoot.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceRoot.relativize(file);
                Path targetFile = targetRoot.resolve(relative);
                if (Files.exists(targetFile)) {
                    return FileVisitResult.CONTINUE;
                }

                Path parent = targetFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
                imported[0]++;
                return FileVisitResult.CONTINUE;
            }
        });

        return imported[0];
    }

    private static Path normalizePath(File file) {
        try {
            return file.toPath().toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to normalize path {}", file.getAbsolutePath(), exception);
            return null;
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
        ServerRestrictionManager.initialize();
        ItemShaderProfiles.ensureLoaded();

        PipelineInitializer.init();

        FontManager fontManager = FontManager.getInstance();

        try {
            fontManager.loadFontFromResources("medium", "strange:fonts/medium.ttf");
        } catch (RuntimeException e) {
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
