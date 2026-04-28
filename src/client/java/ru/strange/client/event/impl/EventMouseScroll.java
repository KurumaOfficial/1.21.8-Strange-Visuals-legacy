package ru.strange.client.event.impl;

import ru.strange.client.event.Event;

public final class EventMouseScroll extends Event {
    private final double mouseX;
    private final double mouseY;
    private final double horizontalAmount;
    private final double verticalAmount;

    public EventMouseScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.horizontalAmount = horizontalAmount;
        this.verticalAmount = verticalAmount;
    }

    public double mouseX() {
        return mouseX;
    }

    public double mouseY() {
        return mouseY;
    }

    public double horizontalAmount() {
        return horizontalAmount;
    }

    public double verticalAmount() {
        return verticalAmount;
    }
}
