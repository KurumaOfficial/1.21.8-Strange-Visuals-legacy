package ru.strange.client.StarterMenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import ru.strange.client.Strange;

public class StrangeVisualsClient implements ClientModInitializer {
    private static Screen suppressedTitleScreen;
    private static boolean vanillaLogoSettingApplied;

    public static void suppressTitleScreenReplacement(Screen titleScreen) {
        suppressedTitleScreen = titleScreen instanceof TitleScreen ? titleScreen : null;
    }

    public static Screen wrapTitleScreenIfNeeded(MinecraftClient client, Screen screen) {
        enableVanillaMonochromeLogoIfPossible(client);
        if (!(screen instanceof TitleScreen) || screen instanceof StarterMenuScreen) {
            return screen;
        }

        if (screen == suppressedTitleScreen) {
            return screen;
        }

        return new StarterMenuScreen(screen);
    }

    @Override
    public void onInitializeClient() {
        enableVanillaMonochromeLogoIfPossible(MinecraftClient.getInstance());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            enableVanillaMonochromeLogoIfPossible(client);
            Screen currentScreen = client.currentScreen;
            if (client.world == null
                    && client.getServer() == null
                    && currentScreen != null
                    && (currentScreen instanceof TitleScreen || currentScreen instanceof StarterMenuScreen)) {
                AltStartupSessionSync.applyIfPossible(client);
            }

            if (suppressedTitleScreen != null && currentScreen != suppressedTitleScreen) {
                suppressedTitleScreen = null;
            }
        });
    }

    private static void enableVanillaMonochromeLogoIfPossible(MinecraftClient client) {
        if (vanillaLogoSettingApplied || client == null || client.options == null) {
            return;
        }

        try {
            client.options.getMonochromeLogo().setValue(true);
            client.options.write();
            vanillaLogoSettingApplied = true;
        } catch (RuntimeException exception) {
            Strange.LOGGER.debug("Failed to persist vanilla monochrome splash setting", exception);
        }
    }
}
