package ru.strange.client.rpc;

import ru.strange.client.Strange;
import ru.strange.client.utils.Helper;

public class RPC implements Helper {
    private static final String APPLICATION_ID = "1482479004177924197";
    private static final String LARGE_IMAGE_KEY = "https://files.catbox.moe/acfxo6.gif";
    private static final long UPDATE_INTERVAL_MS = 2000L;

    public static final DiscordRichPresence presence = new DiscordRichPresence();
    public static boolean started;
    private static Thread thread;
    private static DiscordRPC activeRpc;

    public synchronized void startRpc() {
        if (!DiscordRPC.Loader.isAvailable() || started) {
            return;
        }

        DiscordRPC rpc = DiscordRPC.Loader.getInstance();
        if (rpc == null) {
            return;
        }

        started = true;
        activeRpc = rpc;

        DiscordEventHandlers handlers = new DiscordEventHandlers();
        rpc.Discord_Initialize(APPLICATION_ID, handlers, true, "");

        presence.startTimestamp = System.currentTimeMillis() / 1000L;
        updatePresence();
        rpc.Discord_UpdatePresence(presence);

        thread = new Thread(() -> runCallbackLoop(rpc), "TH-RPC-Handler");

        thread.setDaemon(true);
        thread.start();
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
                    updatePresence();
                    rpc.Discord_UpdatePresence(presence);
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

    private static void updatePresence() {
        presence.details = "Version: " + Strange.getDisplayVersion();
        presence.state = "Best free visuals client";
        presence.largeImageText = Strange.name + " - 1.21.8";
        presence.largeImageKey = LARGE_IMAGE_KEY;
        presence.button_label_1 = "Telegram";
        presence.button_url_1 = "https://t.me/AlephStudio_Official";
        presence.button_label_2 = "Discord";
        presence.button_url_2 = "https://discord.gg/hpXNAADfmk";
    }
}
