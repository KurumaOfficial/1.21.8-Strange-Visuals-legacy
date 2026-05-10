package ru.strange.client.event.impl;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import ru.strange.client.event.Event;

public class EventHandAnimation extends Event {
    private MatrixStack matrices;
    private Hand hand;
    private float swingProgress;

    public EventHandAnimation(MatrixStack matrices, Hand hand, float swingProgress) {
        this.matrices = matrices;
        this.hand = hand;
        this.swingProgress = swingProgress;
    }

    public MatrixStack getMatrices() {
        return matrices;
    }

    public Hand getHand() {
        return hand;
    }

    public float getSwingProgress() {
        return swingProgress;
    }

    public void setMatrices(MatrixStack matrices) {
        this.matrices = matrices;
    }

    public void setHand(Hand hand) {
        this.hand = hand;
    }

    public void setSwingProgress(float swingProgress) {
        this.swingProgress = swingProgress;
    }
}

