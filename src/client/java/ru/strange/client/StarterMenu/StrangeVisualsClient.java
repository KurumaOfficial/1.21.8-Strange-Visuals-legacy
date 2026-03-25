package ru.strange.client.StarterMenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;

public class StrangeVisualsClient implements ClientModInitializer {
    private boolean replacingTitleScreen;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen currentScreen = client.currentScreen;
            if (client.world == null && client.getServer() == null) {
                if (currentScreen != null && (currentScreen.getClass() == TitleScreen.class || currentScreen instanceof StarterMenuScreen)) {
                    AltStartupSessionSync.applyIfPossible(client);
                }
            }

            if (replacingTitleScreen) {
                return;
            }

            if (currentScreen == null || currentScreen.getClass() != TitleScreen.class) {
                return;
            }

            if (client.world != null || client.getServer() != null || client.getOverlay() != null) {
                return;
            }

            replacingTitleScreen = true;
            try {
                client.setScreen(new StarterMenuScreen(currentScreen));
            } finally {
                replacingTitleScreen = false;
            }
        });
    }
}
