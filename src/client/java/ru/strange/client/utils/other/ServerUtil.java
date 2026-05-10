package ru.strange.client.utils.other;

import net.minecraft.client.MinecraftClient;

import java.util.Locale;

public final class ServerUtil {
    private static volatile ServerContext cachedContext;
    private static volatile long cacheTimestamp;
    private static final long CACHE_TTL_MS = 2000L;

    private ServerUtil() {
    }

    public static String getServerId() {
        return getServerContext().id();
    }

    public static String getServerName() {
        return getServerContext().name();
    }

    public static String getServerAddress() {
        return getServerContext().address();
    }

    public static ServerContext getServerContext() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return new ServerContext("unknown", "", "", "");
        }

        long now = System.currentTimeMillis();
        ServerContext cached = cachedContext;
        boolean volatileState = mc.isInSingleplayer() || mc.getCurrentServerEntry() == null;
        if (!volatileState && cached != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            return cached;
        }

        ServerContext result = resolveContext();
        cachedContext = result;
        cacheTimestamp = now;
        return result;
    }

    public static void invalidateCache() {
        cachedContext = null;
        cacheTimestamp = 0L;
    }

    public static boolean isHolyWorld() {
        return "holyworld".equals(getServerId());
    }

    public static boolean isFuntime() {
        return "funtime".equals(getServerId());
    }

    public static String normalizeServerToken(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static ServerContext resolveContext() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return new ServerContext("unknown", "", "", "");
        }
        if (mc.isInSingleplayer()) {
            return new ServerContext("singleplayer", "singleplayer", "singleplayer", "singleplayer");
        }

        var serverEntry = mc.getCurrentServerEntry();
        if (serverEntry == null) {
            return new ServerContext("unknown", "", "", "");
        }

        String name = normalizeServerToken(serverEntry.name);
        String address = normalizeServerToken(serverEntry.address);
        String combined = (name + " " + address).trim();
        String id = combined.isEmpty() ? "unknown" : combined;

        if (combined.contains("holyworld") || combined.contains("holy-world")) {
            id = "holyworld";
        } else if (combined.contains("funtime")) {
            id = "funtime";
        }

        return new ServerContext(id, name, address, combined);
    }

    public record ServerContext(String id, String name, String address, String combined) {
    }
}
