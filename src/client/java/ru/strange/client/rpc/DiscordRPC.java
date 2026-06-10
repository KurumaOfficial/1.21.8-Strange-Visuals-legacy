package ru.strange.client.rpc;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public interface DiscordRPC extends Library {
    class Loader {
        private static DiscordRPC instance;
        private static boolean loaded = false;

        public static DiscordRPC getInstance() {
            if (!loaded) {
                loaded = true;
                try {
                    instance = Native.loadLibrary("discord-rpc", DiscordRPC.class);
                } catch (UnsatisfiedLinkError firstError) {
                    instance = loadBundledLibrary();
                }
            }
            return instance;
        }

        public static boolean isAvailable() {
            return getInstance() != null;
        }

        private static DiscordRPC loadBundledLibrary() {
            String resourcePath = resolveBundledResourcePath();
            if (resourcePath == null) {
                return null;
            }

            try (InputStream stream = DiscordRPC.class.getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    return null;
                }

                String suffix = resourcePath.endsWith(".dll") ? ".dll" : resourcePath.endsWith(".dylib") ? ".dylib" : ".so";
                Path extracted = Files.createTempFile("strange-discord-rpc-", suffix);
                Files.copy(stream, extracted, StandardCopyOption.REPLACE_EXISTING);
                extracted.toFile().deleteOnExit();
                return Native.load(extracted.toAbsolutePath().toString(), DiscordRPC.class);
            } catch (IOException | UnsatisfiedLinkError exception) {
                return null;
            }
        }

        private static String resolveBundledResourcePath() {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String arch = System.getProperty("os.arch", "").toLowerCase();

            if (osName.contains("win")) {
                if (arch.contains("64") || arch.contains("amd64") || arch.contains("x86_64")) {
                    return "/natives/win32-x86-64/discord-rpc.dll";
                }
                return null;
            }

            if (osName.contains("mac")) {
                return "/natives/darwin/libdiscord-rpc.dylib";
            }

            if (osName.contains("linux")) {
                return "/natives/linux-x86-64/libdiscord-rpc.so";
            }

            return null;
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
