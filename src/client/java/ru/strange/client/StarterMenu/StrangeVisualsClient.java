package ru.strange.client.StarterMenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;

public class StrangeVisualsClient implements ClientModInitializer {

    private boolean shown = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!shown && client.currentScreen instanceof TitleScreen) {
                shown = true;
                client.setScreen(new StarterMenuScreen());
            }
        });
    }
}