package ru.strange.client.renderengine.font;

import me.x150.renderer.fontng.FTLibrary;
import me.x150.renderer.fontng.Font;
import me.x150.renderer.fontng.FontScalingRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Представляет семейство шрифтов (один файл шрифта).
 */
public class FontFamily implements AutoCloseable {
    private final String name;
    private final String filePath;
    private final int faceIndex;
    private final FTLibrary library;
    
    private final Map<Integer, Font> sizeCache = new HashMap<>();
    
    private int defaultSize = 16;
    
    public FontFamily(@NotNull String name, @NotNull String filePath, int faceIndex, @NotNull FTLibrary library) {
        this.name = name;
        this.filePath = filePath;
        this.faceIndex = faceIndex;
        this.library = library;
    }
    
    public FontFamily withDefaultSize(int size) {
        this.defaultSize = size;
        return this;
    }
    
    @NotNull
    public Font getSize(int size) {
        return sizeCache.computeIfAbsent(size, s -> {
            Font font = new Font(library, filePath, faceIndex, s);
            FontScalingRegistry.register(font);
            return font;
        });
    }
    
    @NotNull
    public Font getDefault() {
        return getSize(defaultSize);
    }
    
    public void preloadSizes(int... sizes) {
        for (int size : sizes) {
            getSize(size);
        }
    }
    
    public void clearSize(int size) {
        Font font = sizeCache.remove(size);
        if (font != null) {
            font.close();
        }
    }
    
    public void clearAllSizes() {
        sizeCache.values().forEach(Font::close);
        sizeCache.clear();
    }
    
    public int getCachedSizeCount() {
        return sizeCache.size();
    }
    
    @Override
    public void close() {
        clearAllSizes();
    }
    
    @Override
    public String toString() {
        return String.format("FontFamily{name='%s', cachedSizes=%d, defaultSize=%d}", 
                           name, sizeCache.size(), defaultSize);
    }

    public String getName() {
        return name;
    }

    public int getDefaultSize() {
        return defaultSize;
    }
}