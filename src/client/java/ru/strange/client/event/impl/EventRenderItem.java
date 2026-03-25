package ru.strange.client.event.impl;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import ru.strange.client.event.Event;

public class EventRenderItem extends Event {
    private final MatrixStack matrix;
    private final Hand hand;
    private final ItemStack stack;

    public EventRenderItem(MatrixStack matrix, Hand hand, ItemStack stack) {
        this.matrix = matrix;
        this.hand = hand;
        this.stack = stack;
    }

    public MatrixStack getMatrix() {
        return matrix;
    }

    public Hand getHand() {
        return hand;
    }

    public ItemStack getStack() {
        return stack;
    }

    public boolean isRightHand() {
        return hand == Hand.MAIN_HAND;
    }
}
