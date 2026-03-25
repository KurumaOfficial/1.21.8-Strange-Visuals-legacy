package ru.strange.client.event.impl;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import ru.strange.client.event.Event;

public class EventGlassHandsRender extends Event {

    public enum Phase {
        PRE,
        POST
    }

    private final Phase phase;
    private final MatrixStack matrices;
    private final float tickDelta;
    private final Hand hand;
    private final ItemStack stack;

    public EventGlassHandsRender(Phase phase, MatrixStack matrices, float tickDelta, Hand hand, ItemStack stack) {
        this.phase = phase;
        this.matrices = matrices;
        this.tickDelta = tickDelta;
        this.hand = hand;
        this.stack = stack;
    }

    public Phase getPhase() {
        return phase;
    }

    public MatrixStack getMatrices() {
        return matrices;
    }

    public float getTickDelta() {
        return tickDelta;
    }

    public Hand getHand() {
        return hand;
    }

    public ItemStack getStack() {
        return stack;
    }

    public boolean isMainHand() {
        return hand == Hand.MAIN_HAND;
    }
}
