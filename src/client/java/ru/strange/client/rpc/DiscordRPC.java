package ru.strange.client.rpc;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface DiscordRPC extends Library {
    class Loader {
        private static DiscordRPC instance;
        private static boolean loaded = false;

        public static DiscordRPC getInstance() {
            if (!loaded) {
                loaded = true;
                try {
                    instance = Native.loadLibrary("discord-rpc", DiscordRPC.class);
                } catch (UnsatisfiedLinkError e) {
                    instance = null;
                }
            }
            return instance;
        }

        public static boolean isAvailable() {
            return getInstance() != null;
        }
    }

    void Discord_UpdateHandlers(final DiscordEventHandlers p0);

    void Discord_UpdatePresence(final DiscordRichPresence p0);

    void Discord_Respond(final String p0, final int p1);

    void Discord_Register(final String p0, final String p1);

    void Discord_Shutdown();

    void Discord_UpdateConnection();

    void Discord_RegisterSteamGame(final String p0, final String p1);

    void Discord_RunCallbacks();

    void Discord_Initialize(final String p0, final DiscordEventHandlers p1, final boolean p2, final String p3);

    void Discord_ClearPresence();
}
