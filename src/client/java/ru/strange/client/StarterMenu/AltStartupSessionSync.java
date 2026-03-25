package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;

import java.io.File;

public final class AltStartupSessionSync {
    private static final File ALTS_FILE = new File(Strange.root, "alts.json");
    private static final AltAccountStore ACCOUNT_STORE = new AltAccountStore(ALTS_FILE);
    private static final AltSessionService SESSION_SERVICE = new AltSessionService();

    private static AltAccountStore.LoadedAccounts cachedAccounts;
    private static boolean cacheLoaded;

    private AltStartupSessionSync() {
    }

    public static void refresh() {
        cacheLoaded = false;
        cachedAccounts = null;
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
        if (!cacheLoaded) {
            cachedAccounts = ACCOUNT_STORE.load();
            cacheLoaded = true;
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
}
