package ru.strange.client.renderengine.font;

import me.x150.renderer.fontng.Font;
import me.x150.renderer.fontng.GlyphBuffer;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилиты для работы с текстом
 */
public class FontUtils {
    
    public static List<String> wrapText(@NotNull Font font, @NotNull String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            float width = measureWidth(font, testLine);
            
            if (width > maxWidth && !currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (!currentLine.isEmpty()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
    
    public static List<Text> wrapText(@NotNull Font font, @NotNull Text text, float maxWidth) {
        String plainText = text.getString();
        List<String> wrappedLines = wrapText(font, plainText, maxWidth);
        
        List<Text> result = new ArrayList<>();
        for (String line : wrappedLines) {
            result.add(Text.literal(line).setStyle(text.getStyle()));
        }
        
        return result;
    }
    
    public static String truncate(@NotNull Font font, @NotNull String text, float maxWidth) {
        return truncate(font, text, maxWidth, "...");
    }
    
    public static String truncate(@NotNull Font font, @NotNull String text, float maxWidth, String suffix) {
        float currentWidth = measureWidth(font, text);
        
        if (currentWidth <= maxWidth) {
            return text;
        }
        
        float suffixWidth = measureWidth(font, suffix);
        float targetWidth = maxWidth - suffixWidth;
        
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            String testString = result.toString() + c;
            if (measureWidth(font, testString) > targetWidth) {
                break;
            }
            result.append(c);
        }
        
        return result.toString() + suffix;
    }
    
    public static float measureWidth(@NotNull Font font, @NotNull String text) {
        if (text.isEmpty()) {
            return 0;
        }
        
        GlyphBuffer buffer = new GlyphBuffer();
        buffer.addString(font, text, 0, 0);
        return buffer.maxX - buffer.minX;
    }
    
    public static float measureWidth(@NotNull Font font, @NotNull Text text) {
        GlyphBuffer buffer = new GlyphBuffer();
        buffer.addText(font, text, 0, 0);
        return buffer.maxX - buffer.minX;
    }
    
    public static float measureHeight(@NotNull Font font, @NotNull String text, float maxWidth) {
        List<String> lines = wrapText(font, text, maxWidth);
        return lines.size() * font.height();
    }
    
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }
    
    public static String formatNumberCompact(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1_000_000) {
            return String.format("%.1fK", number / 1000.0);
        } else if (number < 1_000_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else {
            return String.format("%.1fB", number / 1_000_000_000.0);
        }
    }
    
    public static float centerText(@NotNull Font font, @NotNull String text, float containerWidth) {
        float textWidth = measureWidth(font, text);
        return (containerWidth - textWidth) / 2f;
    }
    
    public static float rightAlignText(@NotNull Font font, @NotNull String text, float containerWidth) {
        float textWidth = measureWidth(font, text);
        return containerWidth - textWidth;
    }
    
    public static boolean fitsInWidth(@NotNull Font font, @NotNull String text, float width) {
        return measureWidth(font, text) <= width;
    }
    
    public static int getLineCount(@NotNull Font font, @NotNull String text, float maxWidth) {
        return wrapText(font, text, maxWidth).size();
    }
    
    public static String repeat(char c, int count) {
        return String.valueOf(c).repeat(Math.max(0, count));
    }
    
    public static String repeat(String str, int count) {
        return str.repeat(Math.max(0, count));
    }
}