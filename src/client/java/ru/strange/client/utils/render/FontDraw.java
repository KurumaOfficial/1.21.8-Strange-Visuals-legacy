package ru.strange.client.utils.render;

import net.minecraft.client.gui.DrawContext;
import ru.strange.client.module.impl.other.NameProtect;
import ru.strange.client.renderengine.font.FontRenderer;
import ru.strange.client.utils.Helper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *  Author https://github.com/WhiteWindows1 20.01.2026
 */

public class FontDraw implements Helper {
    private static final Map<RendererKey, FontRenderer> RENDERER_CACHE = new HashMap<>();
    private static final int MAX_WIDTH_CACHE_ENTRIES = 512;
    private static final Map<WidthKey, Float> WIDTH_CACHE = new LinkedHashMap<>(MAX_WIDTH_CACHE_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<WidthKey, Float> eldest) {
            return size() > MAX_WIDTH_CACHE_ENTRIES;
        }
    };

    public static void drawText(FontType f, DrawContext mt,String text ,float x,float y,int size,int color,boolean shadow) {
        text = NameProtect.process(text);
        if (text == null || text.isEmpty()) {
            return;
        }
        if(size == 0) {size = 1;}

        FontRenderer fontRenderer = resolveRenderer(f, size);
        fontRenderer.setShadow(shadow);
        fontRenderer.drawDirect(mt, text, x, y, color);
    }

    public static void drawText(FontType f, DrawContext mt,String text ,float x,float y,int size,int color) {
        drawText(f,mt,text,x,y,size,color,false);
    }

    public static void drawCenter(FontType f, DrawContext mt,String text ,float x,float y,int size,int color,boolean shadow) {
        text = NameProtect.process(text);
        float w = getWidth(f, text, size);
        drawText(f,mt,text, x - w / 2f, y, size, color, shadow);
    }

    public static float getWidth(FontType f,String text,int size) {
        text = NameProtect.process(text);
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        if(size == 0) {size = 1;}

        String family = resolveFamily(f);
        WidthKey key = new WidthKey(family, size, text);
        Float cachedWidth = WIDTH_CACHE.get(key);
        if (cachedWidth != null) {
            return cachedWidth;
        }

        float width = resolveRenderer(f, size).getWidth(text);
        WIDTH_CACHE.put(key, width);
        return width;
    }

    public static float getHeight(FontType f, int size) {
        if(size == 0) {size = 1;}
        return resolveRenderer(f, size).getHeight(size);
    }

    public static float getAscent(FontType f, int size) {
        if(size == 0) {size = 1;}
        return resolveRenderer(f, size).getAscent(size);
    }

    private static FontRenderer resolveRenderer(FontType fontType, int size) {
        String family = resolveFamily(fontType);
        RendererKey key = new RendererKey(family, size);
        return RENDERER_CACHE.computeIfAbsent(key, unused -> FontRenderer.create(family, size));
    }

    private static String resolveFamily(FontType fontType) {
        return fontType == FontType.SEMIBOLD ? "semibold" : "medium";
    }

    private record RendererKey(String family, int size) {
    }

    private record WidthKey(String family, int size, String text) {
    }

    public enum FontType {
        MEDIUM,SEMIBOLD
    }

}
