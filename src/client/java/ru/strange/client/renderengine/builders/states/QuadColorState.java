package ru.strange.client.renderengine.builders.states;

import java.awt.Color;

public record QuadColorState(int color1, int color2, int color3, int color4) {

    public static final QuadColorState TRANSPARENT = new QuadColorState(0, 0, 0, 0);
    public static final QuadColorState WHITE = new QuadColorState(-1, -1, -1, -1);

    public QuadColorState {
        color1 = toABGR(color1);
        color2 = toABGR(color2);
        color3 = toABGR(color3);
        color4 = toABGR(color4);
    }

    public QuadColorState(Color color1, Color color2, Color color3, Color color4) {
        this(color1.getRGB(), color2.getRGB(), color3.getRGB(), color4.getRGB());
    }

    public QuadColorState(Color color) {
        this(color, color, color, color);
    }

    public QuadColorState(int color) {
        this(color, color, color, color);
    }

    private static int toABGR(int argb) {
        return (argb & 0xFF00FF00)
                | ((argb & 0x00FF0000) >> 16)
                | ((argb & 0x000000FF) << 16);
    }
}
