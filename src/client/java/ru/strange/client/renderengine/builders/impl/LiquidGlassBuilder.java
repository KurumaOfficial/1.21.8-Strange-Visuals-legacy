package ru.strange.client.renderengine.builders.impl;

import ru.strange.client.renderengine.builders.AbstractBuilder;
import ru.strange.client.renderengine.builders.states.QuadColorState;
import ru.strange.client.renderengine.builders.states.QuadRadiusState;
import ru.strange.client.renderengine.builders.states.SizeState;
import ru.strange.client.renderengine.renderers.LiquidGlassRenderer;

public final class LiquidGlassBuilder extends AbstractBuilder<LiquidGlassRenderer> {

    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private float smoothness;
    private float blurRadius;
    private float fresnelPower;
    private float distortStrength;

    public LiquidGlassBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public LiquidGlassBuilder size(float width, float height) {
        this.size = new SizeState(width, height);
        return this;
    }

    public LiquidGlassBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public LiquidGlassBuilder radius(float radius) {
        this.radius = new QuadRadiusState(radius);
        return this;
    }

    public LiquidGlassBuilder radius(float r1, float r2, float r3, float r4) {
        this.radius = new QuadRadiusState(r1, r2, r3, r4);
        return this;
    }

    public LiquidGlassBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public LiquidGlassBuilder color(int color) {
        this.color = new QuadColorState(color);
        return this;
    }

    public LiquidGlassBuilder color(java.awt.Color color) {
        this.color = new QuadColorState(color);
        return this;
    }

    public LiquidGlassBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    public LiquidGlassBuilder blurRadius(float blurRadius) {
        this.blurRadius = blurRadius;
        return this;
    }

    public LiquidGlassBuilder fresnelPower(float fresnelPower) {
        this.fresnelPower = fresnelPower;
        return this;
    }

    public LiquidGlassBuilder distortStrength(float distortStrength) {
        this.distortStrength = distortStrength;
        return this;
    }

    @Override
    protected LiquidGlassRenderer _build() {
        return new LiquidGlassRenderer(
            this.size,
            this.radius,
            this.color,
            this.smoothness,
            this.blurRadius,
            this.fresnelPower,
            this.distortStrength
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.WHITE;
        this.smoothness = 1.0f;
        this.blurRadius = 15.0f;
        this.fresnelPower = 2.0f;
        this.distortStrength = 0.03f; 
    }
}
