package ru.strange.client.renderengine.font;

import me.x150.renderer.fontng.Font;
import me.x150.renderer.fontng.GlyphBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Кэшированный текст для максимальной производительности.
 */
public class CachedText implements AutoCloseable {
    private final Font font;
    private final GlyphBuffer buffer;
    
    private final float width;
    private final float height;

    private String cachedPlainText;
    private Text cachedStyledText;
    
    public CachedText(@NotNull Font font, @NotNull String text) {
        this(font, text, null);
    }
    
    public CachedText(@NotNull Font font, @NotNull Text text) {
        this(font, null, text);
    }
    
    private CachedText(@NotNull Font font, @Nullable String plainText, @Nullable Text styledText) {
        this.font = font;
        this.buffer = new GlyphBuffer();
        this.cachedPlainText = plainText;
        this.cachedStyledText = styledText;
        
        if (plainText != null) {
            buffer.addString(font, plainText, 0, 0);
        } else if (styledText != null) {
            buffer.addText(font, styledText, 0, 0);
        }
        
        buffer.offsetToTopLeft();
        
        this.width = buffer.maxX - buffer.minX;
        this.height = buffer.maxY - buffer.minY;
    }
    
    public static CachedText create(@NotNull Font font, @NotNull String text, int color) {
        Text styled = Text.literal(text).setStyle(Style.EMPTY.withColor(color));
        return new CachedText(font, styled);
    }
    
    public void draw(@NotNull DrawContext context, float x, float y) {
        buffer.draw(context, x, y);
    }
    
    public void drawCentered(@NotNull DrawContext context, float x, float y) {
        buffer.draw(context, x - width / 2f, y);
    }
    
    public void drawRight(@NotNull DrawContext context, float x, float y) {
        buffer.draw(context, x - width, y);
    }
    
    public void update(@NotNull String newText) {
        if (newText.equals(cachedPlainText)) {
            return;
        }
        
        cachedPlainText = newText;
        cachedStyledText = null;
        
        buffer.clear();
        buffer.addString(font, newText, 0, 0);
        buffer.offsetToTopLeft();
    }
    
    public void update(@NotNull Text newText) {
        cachedStyledText = newText;
        cachedPlainText = null;
        
        buffer.clear();
        buffer.addText(font, newText, 0, 0);
        buffer.offsetToTopLeft();
    }
    
    @Nullable
    public String getText() {
        return cachedPlainText;
    }
    
    @Nullable
    public Text getStyledText() {
        return cachedStyledText;
    }
    
    @Override
    public void close() {
        buffer.clear();
    }
    
    public static class Builder {
        private FontFamily family;
        private int size = 16;
        private String plainText;
        private Text styledText;
        private Integer color;
        
        public Builder family(FontFamily family) {
            this.family = family;
            return this;
        }
        
        public Builder family(String familyName) {
            this.family = FontManager.getInstance().getFamilyOrDefault(familyName);
            return this;
        }
        
        public Builder size(int size) {
            this.size = size;
            return this;
        }
        
        public Builder text(String text) {
            this.plainText = text;
            this.styledText = null;
            return this;
        }
        
        public Builder text(Text text) {
            this.styledText = text;
            this.plainText = null;
            return this;
        }
        
        public Builder color(int color) {
            this.color = color;
            return this;
        }
        
        public CachedText build() {
            if (family == null) {
                family = FontManager.getInstance().getDefaultFamily();
            }
            
            Font font = family.getSize(size);
            
            if (plainText != null) {
                if (color != null) {
                    return CachedText.create(font, plainText, color);
                }
                return new CachedText(font, plainText);
            } else if (styledText != null) {
                if (color != null) {
                    styledText = styledText.copy().setStyle(
                        styledText.getStyle().withColor(color)
                    );
                }
                return new CachedText(font, styledText);
            }
            
            throw new IllegalStateException("No text specified");
        }
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
    
    public static Builder builder() {
        return new Builder();
    }
}