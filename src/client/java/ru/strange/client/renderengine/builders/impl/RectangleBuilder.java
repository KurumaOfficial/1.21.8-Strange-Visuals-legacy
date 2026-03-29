package ru.strange.client.renderengine.builders.impl;

import ru.strange.client.renderengine.builders.AbstractBuilder;
import ru.strange.client.renderengine.builders.states.QuadColorState;
import ru.strange.client.renderengine.builders.states.QuadRadiusState;
import ru.strange.client.renderengine.builders.states.SizeState;
import ru.strange.client.renderengine.renderers.RectangleRenderer;

public final class RectangleBuilder extends AbstractBuilder<RectangleRenderer> {

    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private float smoothness;

    public RectangleBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public RectangleBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public RectangleBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public RectangleBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    @Override
    protected RectangleRenderer _build() {
        return new RectangleRenderer(
            this.size,
            this.radius,
            this.color,
            this.smoothness
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.TRANSPARENT;
        this.smoothness = 1.0f;
    }

}