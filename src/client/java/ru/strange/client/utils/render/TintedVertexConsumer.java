package ru.strange.client.utils.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

public final class TintedVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float mix;

    public TintedVertexConsumer(VertexConsumer delegate, float mix) {
        this.delegate = delegate;
        this.mix = mix;
    }

    public static VertexConsumerProvider wrap(VertexConsumerProvider provider, float mix) {
        return layer -> new TintedVertexConsumer(provider.getBuffer(layer), mix);
    }

    private static int mixChannel(int channel, int target, float mix) {
        return Math.max(0, Math.min(255, Math.round(channel + (target - channel) * mix)));
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(
                mixChannel(red, 85, mix),
                mixChannel(green, 255, mix),
                mixChannel(blue, 115, mix),
                alpha
        );
        return this;
    }

    @Override
    public VertexConsumer color(int argb) {
        return color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >> 24 & 255);
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int uv) {
        delegate.overlay(uv);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int uv) {
        delegate.light(uv);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }
}
