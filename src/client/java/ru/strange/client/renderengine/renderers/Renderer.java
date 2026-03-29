package ru.strange.client.renderengine.renderers;

import net.minecraft.client.gui.DrawContext;

public interface Renderer {
    void render(double x, double y, DrawContext context);
}