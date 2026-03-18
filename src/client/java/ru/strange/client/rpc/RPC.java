package ru.strange.client.rpc;

import ru.strange.client.utils.Helper;

public class RPC implements Helper {

    public static DiscordRichPresence presence = new DiscordRichPresence();
    public static boolean started;
    private static Thread thread;

    public void startRpc() {
        // Проверяем, доступна ли библиотека Discord RPC
        if (!DiscordRPC.Loader.isAvailable()) {
            return;
        }

        DiscordRPC rpc = DiscordRPC.Loader.getInstance();
        if (!started) {
            started = true;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            rpc.Discord_Initialize("1482479004177924197", handlers, true, "");
            presence.startTimestamp = (System.currentTimeMillis() / 1000L);
            presence.largeImageText = "Strange Visuals - 1.21.8";
            rpc.Discord_UpdatePresence(presence);
            thread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    rpc.Discord_RunCallbacks();
                    presence.details = "Version: 1.0";
                    presence.state = "Лучший бесплатный визуал";

                    presence.button_label_1 = "Telegram";
                    presence.button_url_1 = "";

                    presence.button_label_2 = "Discord";
                    presence.button_url_2 = "https://discord.gg/hpXNAADfmk";

                    presence.largeImageKey = "https://i.ibb.co/V0DRRxQn/2026-03-16-182056451.png";

                    rpc.Discord_UpdatePresence(presence);
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException ignored) {
                    }
                }
            }, "TH-RPC-Handler");
            thread.start();

        }
    }
}
