package ru.strange.client.renderengine.builders.impl;

import ru.strange.client.renderengine.builders.AbstractBuilder;
import ru.strange.client.renderengine.builders.states.QuadColorState;
import ru.strange.client.renderengine.builders.states.QuadRadiusState;
import ru.strange.client.renderengine.builders.states.SizeState;
import ru.strange.client.renderengine.renderers.ShadowRenderer;

public final class ShadowBuilder extends AbstractBuilder<ShadowRenderer> {

    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private float smoothness;
    private float offsetX;
    private float offsetY;
    private float blurRadius;

    public ShadowBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public ShadowBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public ShadowBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public ShadowBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    public ShadowBuilder offset(float offsetX, float offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        return this;
    }

    public ShadowBuilder offsetX(float offsetX) {
        this.offsetX = offsetX;
        return this;
    }

    public ShadowBuilder offsetY(float offsetY) {
        this.offsetY = offsetY;
        return this;
    }

    public ShadowBuilder blurRadius(float blurRadius) {
        this.blurRadius = blurRadius;
        return this;
    }

    @Override
    protected ShadowRenderer _build() {
        return new ShadowRenderer(
            this.size,
            this.radius,
            this.color,
            this.smoothness,
            this.offsetX,
            this.offsetY,
            this.blurRadius
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.TRANSPARENT;
        this.smoothness = 1.0f;
        this.offsetX = 2.0f;
        this.offsetY = 2.0f;
        this.blurRadius = 4.0f;
    }

}
