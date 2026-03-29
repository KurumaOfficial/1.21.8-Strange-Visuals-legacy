package ru.strange.client.event.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import ru.strange.client.event.Event;

import java.util.Objects;

public class EventScreen extends Event {
    private final MinecraftClient client;
    private final DrawContext drawContext;

    public EventScreen(
            MinecraftClient client,
            DrawContext drawContext
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.drawContext = drawContext;
    }

    public MinecraftClient client() {
        return client;
    }

    public DrawContext drawContext() {
        return drawContext;
    }
}