package ru.strange.client.utils.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.api.Module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ServerRestrictionManager {
    private static final long BLOCKED_ATTEMPT_NOTIFY_COOLDOWN_MS = 2500L;

    private static final Set<String> notifiedRestrictions = new HashSet<>();
    private static final Set<Module> forcedDisabledModules = new HashSet<>();
    private static final Map<String, Long> blockedAttemptNotifications = new HashMap<>();

    private static String lastServerKey = "";
    private static String lastProfileName = "";
    private static ServerRulesConfig.ServerRuleProfile activeProfile;

    private ServerRestrictionManager() {
    }

    public static void initialize() {
        ServerRulesConfig.ensureLoaded();
    }

    public static void tick() {
        initialize();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }

        if (mc.player == null || mc.world == null) {
            activeProfile = null;
            syncRestrictions(null);
            lastServerKey = "";
            lastProfileName = "";
            notifiedRestrictions.clear();
            blockedAttemptNotifications.clear();
            return;
        }

        boolean configChanged = ServerRulesConfig.reloadIfModified();
        ServerUtil.ServerContext context = ServerUtil.getServerContext();
        ServerRulesConfig.ServerRuleProfile profile = ServerRulesConfig.findMatchingProfile(context);
        String serverKey = buildServerKey(context);
        String profileName = profile == null ? "" : profile.getName();

        if (configChanged || !serverKey.equals(lastServerKey) || !Objects.equals(profileName, lastProfileName)) {
            notifiedRestrictions.clear();
            blockedAttemptNotifications.clear();
        }

        activeProfile = profile;
        syncRestrictions(profile);
        lastServerKey = serverKey;
        lastProfileName = profileName;
    }

    public static boolean shouldShow(Module module) {
        return module != null && !isModuleRestricted(module);
    }

    public static boolean preventEnable(Module module, boolean notify) {
        ServerRulesConfig.ServerRuleProfile profile = getCurrentProfile();
        if (module == null || profile == null || !profile.matchesModule(module)) {
            return false;
        }

        if (notify) {
            notifyBlockedAttempt(module, profile);
        }

        return true;
    }

    public static boolean isModuleRestricted(Module module) {
        ServerRulesConfig.ServerRuleProfile profile = getCurrentProfile();
        return profile != null && profile.matchesModule(module);
    }

    public static boolean hasActiveProfile() {
        return getCurrentProfile() != null;
    }

    public static String getActiveProfileName() {
        ServerRulesConfig.ServerRuleProfile profile = getCurrentProfile();
        return profile == null ? "" : profile.getName();
    }

    public static int getHiddenModuleCount() {
        ServerRulesConfig.ServerRuleProfile profile = getCurrentProfile();
        if (profile == null || Strange.get == null || Strange.get.manager == null) {
            return 0;
        }

        return profile.countMatchingModules(Strange.get.manager.getModules());
    }

    public static String getRulesFilePath() {
        return ServerRulesConfig.getConfigFile().getAbsolutePath();
    }

    private static void syncRestrictions(ServerRulesConfig.ServerRuleProfile profile) {
        for (Module module : new ArrayList<>(forcedDisabledModules)) {
            if (profile != null && profile.matchesModule(module)) {
                continue;
            }

            forcedDisabledModules.remove(module);
            if (module != null && !module.enable) {
                module.setEnable(true);
                notifyClient(ModLocalization.tr("server.module.available", module.getLocalizedName()));
            }
        }

        if (profile == null || Strange.get == null || Strange.get.manager == null) {
            return;
        }

        for (Module module : Strange.get.manager.getModules()) {
            if (!profile.matchesModule(module) || !module.enable) {
                continue;
            }

            module.setEnable(false);
            forcedDisabledModules.add(module);

            String notificationKey = profile.getName() + ":" + module.name;
            if (notifiedRestrictions.add(notificationKey)) {
                notifyClient(ModLocalization.tr("server.module.disabled", module.getLocalizedName(), profile.getName()));
            }
        }
    }

    private static ServerRulesConfig.ServerRuleProfile getCurrentProfile() {
        if (activeProfile != null) {
            return activeProfile;
        }

        ServerRulesConfig.ensureLoaded();
        ServerRulesConfig.reloadIfModified();
        return ServerRulesConfig.findMatchingProfile(ServerUtil.getServerContext());
    }

    private static void notifyBlockedAttempt(Module module, ServerRulesConfig.ServerRuleProfile profile) {
        String key = profile.getName() + ":" + module.name;
        long now = System.currentTimeMillis();
        Long lastNotificationAt = blockedAttemptNotifications.get(key);
        if (lastNotificationAt != null && (now - lastNotificationAt) < BLOCKED_ATTEMPT_NOTIFY_COOLDOWN_MS) {
            return;
        }

        blockedAttemptNotifications.put(key, now);
        notifyClient(ModLocalization.tr("server.module.hidden", module.getLocalizedName(), profile.getName()));
    }

    private static String buildServerKey(ServerUtil.ServerContext context) {
        if (context == null) {
            return "";
        }

        return context.id() + "|" + context.name() + "|" + context.address();
    }

    private static void notifyClient(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[" + ModLocalization.tr("server.prefix") + "] " + message));
        }
    }
}
