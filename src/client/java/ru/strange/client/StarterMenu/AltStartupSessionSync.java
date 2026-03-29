package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AltStartupSessionSync {
    private static final File ALTS_FILE = new File(Strange.root, "alts.json");
    private static final File ALTS_TEMP_FILE = new File(Strange.root, "alts.json.tmp");
    private static final AltAccountStore ACCOUNT_STORE = new AltAccountStore(ALTS_FILE);
    private static final AltSessionService SESSION_SERVICE = new AltSessionService();

    private static AltAccountStore.LoadedAccounts cachedAccounts;
    private static boolean cacheLoaded;
    private static long cachedFingerprint = Long.MIN_VALUE;

    private AltStartupSessionSync() {
    }

    public static void refresh() {
        cacheLoaded = false;
        cachedAccounts = null;
        cachedFingerprint = Long.MIN_VALUE;
    }

    public static void applyIfPossible(MinecraftClient client) {
        if (client == null || client.world != null) {
            return;
        }

        AltAccountStore.LoadedAccounts loaded = loadAccounts();
        String targetName = resolvePreferredName(loaded, SESSION_SERVICE.getCurrentName(client));
        if (targetName == null) {
            return;
        }

        String currentName = SESSION_SERVICE.getCurrentName(client);
        if (targetName.equalsIgnoreCase(currentName)) {
            return;
        }

        AltSessionService.SwitchResult result = SESSION_SERVICE.switchToOfflineProfile(client, targetName);
        if (result == AltSessionService.SwitchResult.INVALID_NAME) {
            refresh();
        }
    }

    public static String getPreferredStoredName() {
        return resolvePreferredName(loadAccounts(), null);
    }

    private static AltAccountStore.LoadedAccounts loadAccounts() {
        long currentFingerprint = storageFingerprint();
        if (!cacheLoaded || currentFingerprint != cachedFingerprint) {
            cachedAccounts = ACCOUNT_STORE.load();
            cacheLoaded = true;
            cachedFingerprint = currentFingerprint;
        }
        return cachedAccounts;
    }

    private static String resolvePreferredName(AltAccountStore.LoadedAccounts loaded, String currentName) {
        String selectedName = SESSION_SERVICE.normalizeName(loaded.selectedActiveName());
        if (selectedName != null) {
            return selectedName;
        }

        if (currentName != null) {
            for (AltAccount account : loaded.active()) {
                if (account.name.equalsIgnoreCase(currentName)) {
                    return account.name;
                }
            }
        }

        if (!loaded.active().isEmpty()) {
            return SESSION_SERVICE.normalizeName(loaded.active().get(0).name);
        }
        return null;
    }

    private static long storageFingerprint() {
        long primary = fileFingerprint(ALTS_FILE.toPath());
        long temporary = fileFingerprint(ALTS_TEMP_FILE.toPath());
        return 31L * primary + temporary;
    }

    private static long fileFingerprint(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return 0L;
        }

        try {
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            long size = Files.size(path);
            return 31L * lastModified + size;
        } catch (IOException exception) {
            Strange.LOGGER.debug("Failed to fingerprint alt storage {}", path.toAbsolutePath(), exception);
            return Long.MIN_VALUE;
        }
    }
}
