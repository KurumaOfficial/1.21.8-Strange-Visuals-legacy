package ru.strange.client.event.impl;

import ru.strange.client.event.Event;

public final class EventMouseInput extends Event {

    private final long window;
    private final int button;
    private final int action;
    private final int modifiers;

    public EventMouseInput(long window, int button, int action, int modifiers) {
        this.window = window;
        this.button = button;
        this.action = action;
        this.modifiers = modifiers;
    }

    public long window() {
        return window;
    }

    public int button() {
        return button;
    }

    public int action() {
        return action;
    }

    public int modifiers() {
        return modifiers;
    }
}
