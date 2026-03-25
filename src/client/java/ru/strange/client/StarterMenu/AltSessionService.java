package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import ru.strange.client.Strange;
import ru.strange.client.mixin.MinecraftClientAccessor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AltSessionService {
    private static final Pattern OFFLINE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public enum SwitchResult {
        SUCCESS,
        INVALID_NAME,
        IN_GAME,
        FAILED
    }

    public SwitchResult switchToOfflineProfile(MinecraftClient client, String name) {
        try {
            if (client == null) {
                return SwitchResult.FAILED;
            }

            if (client.world != null) {
                return SwitchResult.IN_GAME;
            }

            String normalizedName = normalizeName(name);
            if (normalizedName == null) {
                return SwitchResult.INVALID_NAME;
            }

            Session session = new Session(
                    normalizedName,
                    UUID.nameUUIDFromBytes(("OfflinePlayer:" + normalizedName).getBytes(StandardCharsets.UTF_8)),
                    "0",
                    Optional.empty(),
                    Optional.empty(),
                    Session.AccountType.LEGACY
            );

            ((MinecraftClientAccessor) client).setSession(session);
            Session appliedSession = client.getSession();
            if (appliedSession == null) {
                return SwitchResult.FAILED;
            }

            if (!normalizedName.equalsIgnoreCase(appliedSession.getUsername())) {
                Strange.LOGGER.debug("Offline profile session applied with different visible name: expected={}, actual={}",
                        normalizedName, appliedSession.getUsername());
            }
            return SwitchResult.SUCCESS;
        } catch (Throwable t) {
            Strange.LOGGER.warn("Failed to switch offline profile to {}", name, t);
            return SwitchResult.FAILED;
        }
    }

    public String getCurrentName(MinecraftClient client) {
        try {
            if (client == null || client.getSession() == null) {
                return "Player";
            }
            String username = client.getSession().getUsername();
            return username == null || username.isBlank() ? "Player" : username;
        } catch (Throwable t) {
            Strange.LOGGER.warn("Failed to read current session name", t);
            return "Player";
        }
    }

    public String normalizeName(String name) {
        if (name == null) {
            return null;
        }

        String normalizedName = name.trim();
        if (!OFFLINE_NAME_PATTERN.matcher(normalizedName).matches()) {
            return null;
        }

        return normalizedName;
    }
}
