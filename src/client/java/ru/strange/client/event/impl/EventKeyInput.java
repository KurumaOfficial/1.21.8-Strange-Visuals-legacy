package ru.strange.client.event.impl;

import ru.strange.client.event.Event;

public final class EventKeyInput extends Event {

    private final long window;
    private final int key;
    private final int scancode;
    private final int action;
    private final int modifiers;

    public EventKeyInput(long window, int key, int scancode, int action, int modifiers) {
        this.window = window;
        this.key = key;
        this.scancode = scancode;
        this.action = action;
        this.modifiers = modifiers;
    }

    public long window() {
        return window;
    }

    public int key() {
        return key;
    }

    public int scancode() {
        return scancode;
    }

    public int action() {
        return action;
    }

    public int modifiers() {
        return modifiers;
    }
}