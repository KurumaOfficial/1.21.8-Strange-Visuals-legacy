package ru.strange.client.renderengine.font;

import me.x150.renderer.fontng.Font;
import me.x150.renderer.fontng.GlyphBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Рендерер для текста с батчингом.
 */
public class FontRenderer {
    private final FontFamily family;
    
    private int defaultSize = 16;
    
    private int defaultColor = 0xFFFFFFFF;
    
    private boolean shadow = false;
    
    private int shadowColor = 0x80000000;
    
    private float shadowOffsetX = 1f;
    
    private float shadowOffsetY = 1f;
    
    private GlyphBuffer currentBatch;
    private Font currentFont;
    private final List<RenderTask> tasks = new ArrayList<>();
    
    public FontRenderer(@NotNull FontFamily family) {
        this.family = family;
        this.defaultSize = family.getDefaultSize();
    }
    
    public static FontRenderer create(@NotNull String familyName, int size) {
        FontFamily family = FontManager.getInstance().getFamilyOrDefault(familyName);
        FontRenderer renderer = new FontRenderer(family);
        renderer.setDefaultSize(size);
        return renderer;
    }
    
    public static FontRenderer createDefault() {
        return new FontRenderer(FontManager.getInstance().getDefaultFamily());
    }
    
    public FontRenderer begin() {
        return begin(defaultSize);
    }
    
    public FontRenderer begin(int size) {
        if (currentBatch != null) {
            throw new IllegalStateException("Previous batch not finished");
        }
        
        currentFont = family.getSize(size);
        currentBatch = new GlyphBuffer();
        tasks.clear();
        
        return this;
    }
    
    public FontRenderer draw(@NotNull String text, float x, float y) {
        return draw(text, x, y, defaultColor);
    }
    
    public FontRenderer draw(@NotNull String text, float x, float y, int color) {
        ensureBatchStarted();
        
        if (shadow) {
            drawShadowInternal(text, x, y, color);
        }
        
        tasks.add(new RenderTask(text, x, y, color, null, currentFont));
        return this;
    } public FontRenderer draw(@NotNull String text, double x, double y, int color) {
        ensureBatchStarted();

        if (shadow) {
            drawShadowInternal(text, (float) x, (float) y, color);
        }

        tasks.add(new RenderTask(text, (float) x, (float) y, color, null, currentFont));
        return this;
    }

    
    public FontRenderer draw(@NotNull Text text, float x, float y) {
        ensureBatchStarted();
        
        if (shadow) {
            drawShadowInternal(text, x, y);
        }
        
        tasks.add(new RenderTask(null, x, y, defaultColor, text, currentFont));
        return this;
    }
    
    public FontRenderer drawCentered(@NotNull String text, float x, float y) {
        return drawCentered(text, x, y, defaultColor);
    }
    
    public FontRenderer drawCentered(@NotNull String text, float x, float y, int color) {
        float width = getWidth(text);
        return draw(text, x - width / 2f, y, color);
    }
    
    public FontRenderer drawRight(@NotNull String text, float x, float y) {
        return drawRight(text, x, y, defaultColor);
    }
    
    public FontRenderer drawRight(@NotNull String text, float x, float y, int color) {
        float width = getWidth(text);
        return draw(text, x - width, y, color);
    }
    
    private void drawShadowInternal(String text, float x, float y, int color) {
        tasks.add(new RenderTask(
            text,
            x + shadowOffsetX,
            y + shadowOffsetY,
            applyShadowColor(color),
            null,
            currentFont
        ));
    }
    
    private void drawShadowInternal(Text text, float x, float y) {
        tasks.add(new RenderTask(
            null,
            x + shadowOffsetX,
            y + shadowOffsetY,
            shadowColor,
            text,
            currentFont
        ));
    }
    
    private int applyShadowColor(int originalColor) {
        int shadowAlpha = ColorHelper.getAlpha(shadowColor);
        int r = ColorHelper.getRed(originalColor) / 4;
        int g = ColorHelper.getGreen(originalColor) / 4;
        int b = ColorHelper.getBlue(originalColor) / 4;
        return ColorHelper.getArgb(shadowAlpha, r, g, b);
    }

    public void end(@NotNull DrawContext context) {
        ensureBatchStarted();

        for (RenderTask task : tasks) {
            if (task.styledText != null) {
                currentBatch.addText(task.font, task.styledText, task.x, task.y);
            } else if (task.plainText != null) {
                // ИСПРАВЛЕНО: передаем полный цвет с альфой через TextColor.fromRgb
                Text text = Text.literal(task.plainText)
                        .setStyle(Style.EMPTY.withColor(net.minecraft.text.TextColor.fromRgb(task.color)));
                currentBatch.addText(task.font, text, task.x, task.y);
            }
        }

        if (!tasks.isEmpty()) {
            currentBatch.draw(context, 0, 0);
        }

        currentBatch = null;
        currentFont = null;
        tasks.clear();
    }
    
    public void drawDirect(@NotNull DrawContext context, @NotNull String text, float x, float y) {
        drawDirect(context, text, x, y, defaultColor);
    }
    
    public void drawDirect(@NotNull DrawContext context, @NotNull String text, float x, float y, int color) {
        drawDirect(context, text, x, y, color, defaultSize);
    }

    public void drawDirect(@NotNull DrawContext context, @NotNull String text, float x, float y, int color, int size) {
        Font font = family.getSize(size);
        GlyphBuffer buffer = new GlyphBuffer();

        if (shadow) {
            Text shadowText = Text.literal(text)
                    .setStyle(Style.EMPTY.withColor(net.minecraft.text.TextColor.fromRgb(applyShadowColor(color))));
            buffer.addText(font, shadowText, x + shadowOffsetX, y + shadowOffsetY);
        }

        // ИСПРАВЛЕНО
        Text styledText = Text.literal(text)
                .setStyle(Style.EMPTY.withColor(net.minecraft.text.TextColor.fromRgb(color)));
        buffer.addText(font, styledText, x, y);
        buffer.draw(context, 0, 0);
    }
    
    public float getWidth(@NotNull String text) {
        return getWidth(text, defaultSize);
    }
    
    public float getWidth(@NotNull String text, int size) {
        Font font = family.getSize(size);
        GlyphBuffer buffer = new GlyphBuffer();
        buffer.addString(font, text, 0, 0);
        return buffer.maxX - buffer.minX;
    }
    
    public float getWidth(@NotNull Text text) {
        return getWidth(text, defaultSize);
    }
    
    public float getWidth(@NotNull Text text, int size) {
        Font font = family.getSize(size);
        GlyphBuffer buffer = new GlyphBuffer();
        buffer.addText(font, text, 0, 0);
        return buffer.maxX - buffer.minX;
    }
    
    public float getHeight() {
        return getHeight(defaultSize);
    }
    
    public float getHeight(int size) {
        return family.getSize(size).height();
    }
    
    private void ensureBatchStarted() {
        if (currentBatch == null) {
            throw new IllegalStateException("Batch not started. Call begin() first");
        }
    }
    
    public static class Builder {
        private String familyName = null;
        private int size = 16;
        private int color = 0xFFFFFFFF;
        private boolean shadow = false;
        private int shadowColor = 0x80000000;
        private float shadowOffsetX = 1f;
        private float shadowOffsetY = 1f;
        
        public Builder family(String name) {
            this.familyName = name;
            return this;
        }
        
        public Builder size(int size) {
            this.size = size;
            return this;
        }
        
        public Builder color(int color) {
            this.color = color;
            return this;
        }
        
        public Builder shadow(boolean shadow) {
            this.shadow = shadow;
            return this;
        }
        
        public Builder shadowColor(int color) {
            this.shadowColor = color;
            return this;
        }
        
        public Builder shadowOffset(float x, float y) {
            this.shadowOffsetX = x;
            this.shadowOffsetY = y;
            return this;
        }
        
        public FontRenderer build() {
            FontFamily family = familyName != null
                ? FontManager.getInstance().getFamilyOrDefault(familyName)
                : FontManager.getInstance().getDefaultFamily();
            
            FontRenderer renderer = new FontRenderer(family);
            renderer.setDefaultSize(size);
            renderer.setDefaultColor(color);
            renderer.setShadow(shadow);
            renderer.setShadowColor(shadowColor);
            renderer.setShadowOffsetX(shadowOffsetX);
            renderer.setShadowOffsetY(shadowOffsetY);
            
            return renderer;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }

    public int getDefaultSize() {
        return defaultSize;
    }

    public void setDefaultSize(int defaultSize) {
        this.defaultSize = defaultSize;
    }

    public int getDefaultColor() {
        return defaultColor;
    }

    public void setDefaultColor(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public int getShadowColor() {
        return shadowColor;
    }

    public void setShadowColor(int shadowColor) {
        this.shadowColor = shadowColor;
    }

    public float getShadowOffsetX() {
        return shadowOffsetX;
    }

    public void setShadowOffsetX(float shadowOffsetX) {
        this.shadowOffsetX = shadowOffsetX;
    }

    public float getShadowOffsetY() {
        return shadowOffsetY;
    }

    public void setShadowOffsetY(float shadowOffsetY) {
        this.shadowOffsetY = shadowOffsetY;
    }

    private record RenderTask(
        @Nullable String plainText,
        float x,
        float y,
        int color,
        @Nullable Text styledText,
        @NotNull Font font
    ) {}
}