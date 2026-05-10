package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import ru.strange.client.Strange;
import ru.strange.client.mixin.MinecraftClientAccessor;
import ru.strange.client.utils.other.ServerUtil;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AltSessionService {
    private static final Pattern OFFLINE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final String DEFAULT_USERNAME = "Player";

    public enum SwitchResult {
        SUCCESS,
        INVALID_NAME,
        IN_GAME,
        FAILED
    }

    public record SessionSnapshot(Session session, String username) {
    }

    public SwitchResult switchToOfflineProfile(MinecraftClient client, String name) {
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

        return applySession(client, session, normalizedName) ? SwitchResult.SUCCESS : SwitchResult.FAILED;
    }

    public SessionSnapshot captureSession(MinecraftClient client) {
        if (client == null) {
            return new SessionSnapshot(null, DEFAULT_USERNAME);
        }

        Session session = client.getSession();
        return new SessionSnapshot(session, getCurrentName(client));
    }

    public boolean restoreSession(MinecraftClient client, SessionSnapshot snapshot) {
        if (snapshot == null || snapshot.session() == null) {
            return false;
        }

        return applySession(client, snapshot.session(), snapshot.username());
    }

    public boolean isCurrentProfile(MinecraftClient client, String name) {
        String normalizedName = normalizeName(name);
        return normalizedName != null && normalizedName.equalsIgnoreCase(getCurrentName(client));
    }

    public String getCurrentName(MinecraftClient client) {
        if (client == null || client.getSession() == null) {
            return DEFAULT_USERNAME;
        }

        String username = client.getSession().getUsername();
        return username == null || username.isBlank() ? DEFAULT_USERNAME : username;
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

    private boolean applySession(MinecraftClient client, Session session, String expectedVisibleName) {
        if (client == null || session == null) {
            return false;
        }

        if (!(client instanceof MinecraftClientAccessor accessor)) {
            Strange.LOGGER.warn("MinecraftClient accessor is unavailable during session switch");
            return false;
        }

        accessor.setSession(session);
        refreshClientStateAfterSessionChange();

        Session appliedSession = client.getSession();
        if (appliedSession == null) {
            Strange.LOGGER.warn("Session switch produced a null client session");
            return false;
        }

        if (expectedVisibleName != null && !expectedVisibleName.equalsIgnoreCase(appliedSession.getUsername())) {
            Strange.LOGGER.debug("Session applied with different visible name: expected={}, actual={}",
                    expectedVisibleName, appliedSession.getUsername());
        }
        return true;
    }

    private void refreshClientStateAfterSessionChange() {
        ServerUtil.invalidateCache();
    }
}
