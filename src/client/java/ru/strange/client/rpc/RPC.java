package ru.strange.client.rpc;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;
import ru.strange.client.utils.Helper;

public class RPC implements Helper {
    private static final String APPLICATION_ID = "1511674361118064640";
    /** Asset key uploaded in the Discord Developer Portal for this application. */
    private static final String LARGE_IMAGE_KEY = "strange_logo";
    private static final String DISCORD_INVITE = "https://discord.gg/veePBec9Cn";
    private static final long UPDATE_INTERVAL_MS = 2000L;

    public static final DiscordRichPresence presence = new DiscordRichPresence();
    public static boolean started;
    private static Thread thread;
    private static DiscordRPC activeRpc;

    public synchronized void startRpc() {
        if (started) {
            return;
        }

        if (!DiscordRPC.Loader.isAvailable()) {
            Strange.LOGGER.warn("Discord RPC library is unavailable. Bundle discord-rpc natives or install discord-rpc on PATH.");
            return;
        }

        DiscordRPC rpc = DiscordRPC.Loader.getInstance();
        if (rpc == null) {
            Strange.LOGGER.warn("Discord RPC failed to initialize native library");
            return;
        }

        started = true;
        activeRpc = rpc;

        DiscordEventHandlers handlers = new DiscordEventHandlers();
        handlers.ready = (user) -> Strange.LOGGER.info("Discord RPC connected as {}", user != null ? user.username : "unknown");
        handlers.errored = (errorCode, message) ->
                Strange.LOGGER.warn("Discord RPC error {}: {}", errorCode, message);

        rpc.Discord_Initialize(APPLICATION_ID, handlers, true, "");

        presence.startTimestamp = System.currentTimeMillis() / 1000L;
        pushPresence(rpc);

        thread = new Thread(() -> runCallbackLoop(rpc), "strange-rpc-handler");
        thread.setDaemon(true);
        thread.start();
        Strange.LOGGER.info("Discord RPC started");
    }

    public synchronized void shutdownRpc() {
        started = false;

        Thread worker = thread;
        thread = null;
        if (worker != null) {
            worker.interrupt();
        }

        DiscordRPC rpc = activeRpc;
        activeRpc = null;
        if (rpc == null) {
            return;
        }

        try {
            rpc.Discord_ClearPresence();
            rpc.Discord_Shutdown();
        } catch (Throwable t) {
            Strange.LOGGER.warn("Failed to shut down Discord RPC cleanly", t);
        }
    }

    private void runCallbackLoop(DiscordRPC rpc) {
        try {
            while (started && !Thread.currentThread().isInterrupted()) {
                try {
                    rpc.Discord_RunCallbacks();
                    pushPresence(rpc);
                    Thread.sleep(UPDATE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    Strange.LOGGER.warn("Discord RPC callback loop failed", t);
                    sleepQuietly();
                }
            }
        } finally {
            synchronized (this) {
                if (thread == Thread.currentThread()) {
                    thread = null;
                }
                started = false;
                if (activeRpc == rpc) {
                    activeRpc = null;
                }
            }
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(UPDATE_INTERVAL_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void pushPresence(DiscordRPC rpc) {
        updatePresenceFields();
        presence.write();
        rpc.Discord_UpdatePresence(presence);
    }

    private static void updatePresenceFields() {
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = client.getSession() != null ? client.getSession().getUsername() : "Unknown";
        presence.details = playerName + " | Strange Visuals " + Strange.getDisplayVersion();
        presence.state = "Strange Visual";
        presence.largeImageText = Strange.name;
        presence.largeImageKey = LARGE_IMAGE_KEY;
        presence.smallImageKey = "";
        presence.smallImageText = "";
        presence.button_label_1 = "Discord";
        presence.button_url_1 = DISCORD_INVITE;
        presence.button_label_2 = "";
        presence.button_url_2 = "";
    }
}
