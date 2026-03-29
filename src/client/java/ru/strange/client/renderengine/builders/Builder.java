package ru.strange.client.renderengine.builders;

import ru.strange.client.renderengine.builders.impl.BlurBuilder;
import ru.strange.client.renderengine.builders.impl.BorderBuilder;
import ru.strange.client.renderengine.builders.impl.GifBuilder;
import ru.strange.client.renderengine.builders.impl.LiquidGlassBuilder;
import ru.strange.client.renderengine.builders.impl.RectangleBuilder;
import ru.strange.client.renderengine.builders.impl.ShadowBuilder;
import ru.strange.client.renderengine.builders.impl.TextureBuilder;

public final class Builder {

    private static final RectangleBuilder RECTANGLE_BUILDER = new RectangleBuilder();
    private static final BorderBuilder BORDER_BUILDER = new BorderBuilder();
    private static final TextureBuilder TEXTURE_BUILDER = new TextureBuilder();
    private static final BlurBuilder BLUR_BUILDER = new BlurBuilder();
    private static final ShadowBuilder SHADOW_BUILDER = new ShadowBuilder();
    private static final GifBuilder GIF_BUILDER = new GifBuilder();
    private static final LiquidGlassBuilder LIQUID_GLASS_BUILDER = new LiquidGlassBuilder();

    public static RectangleBuilder rectangle() {
        return RECTANGLE_BUILDER;
    }

    public static BorderBuilder border() {
        return BORDER_BUILDER;
    }

    public static TextureBuilder texture() {
        return TEXTURE_BUILDER;
    }



    public static BlurBuilder blur() {
        return BLUR_BUILDER;
    }

    public static ShadowBuilder shadow() {
        return SHADOW_BUILDER;
    }

    public static GifBuilder gif() {
        return GIF_BUILDER;
    }

    public static LiquidGlassBuilder liquidGlass() {
        return LIQUID_GLASS_BUILDER;
    }

}