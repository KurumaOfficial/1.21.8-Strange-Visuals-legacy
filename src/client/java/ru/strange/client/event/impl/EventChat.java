package ru.strange.client.event.impl;

import net.minecraft.text.Text;
import ru.strange.client.event.Event;

public class EventChat extends Event {
    private final Text message;
    private final String raw;
    private final boolean system;

    public EventChat(Text message, boolean system) {
        this.message = message;
        this.raw = message.getString();
        this.system = system;
    }

    public Text getMessage() {
        return message;
    }

    public String getRaw() {
        return raw;
    }

    public boolean isSystem() {
        return system;
    }
}
