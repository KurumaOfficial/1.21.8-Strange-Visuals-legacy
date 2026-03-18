package ru.strange.client.ui.clickgui.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class GuiRenderTheme extends GuiScreen {
    private static final Map<Theme, Float> hoverAnimations = new HashMap<>();
    private static final Map<Theme, Float> selectAnimations = new HashMap<>();
    private static final float ANIMATION_SPEED = 0.15f;
    
    static {
        for (Theme theme : Theme.values()) {
            hoverAnimations.put(theme, 0f);
            selectAnimations.put(theme, 0f);
        }
    }
    
    public static void renderTheme(DrawContext ctx) {
        if (selectedCategories != Category.Theme) return;
        
        boolean themea = ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE || 
                        ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK || 
                        ThemeManager.getTheme() == Theme.PURPLE || 
                        ThemeManager.getTheme() == Theme.PINK;
        boolean blackTheme = ThemeManager.getTheme() == Theme.BLACK;
        
        float startX = x + 7;
        float startY = y + 64;
        float cardWidth = 102;
        float cardHeight = 26;
        float spacing = 8;
        float columnSpacing = 8;

        for (int index = 0; index < themes.length; index++) {
            Theme theme = themes[index];
            int row = index / 2;
            int col = index % 2;
            
            float drawX = startX + col * (cardWidth + columnSpacing);
            float drawY = startY + row * (cardHeight + spacing);

            // Обновляем анимации
            float targetHover = isHovered(mc.mouse.getX(), mc.mouse.getY(), drawX, drawY, cardWidth, cardHeight) ? 1f : 0f;
            float currentHover = hoverAnimations.get(theme);
            currentHover += (targetHover - currentHover) * ANIMATION_SPEED;
            hoverAnimations.put(theme, currentHover);
            
            float targetSelect = ThemeManager.getTheme() == theme ? 1f : 0f;
            float currentSelect = selectAnimations.get(theme);
            currentSelect += (targetSelect - currentSelect) * ANIMATION_SPEED;
            selectAnimations.put(theme, currentSelect);

            // Эффект поднятия при наведении (просто смещаем Y)
            float liftOffset = currentHover * 2f;
            float finalDrawY = drawY - liftOffset;

            // Граница с анимацией выбора
            int borderAlpha = (int)(80 + currentSelect * 100 + currentHover * 50);
            Color borderColor = blackTheme ? 
                new Color(255, 255, 255, Math.min(255, borderAlpha)) : 
                themea ? new Color(0, 0, 0, Math.min(255, borderAlpha / 2)) : 
                new Color(0, 0, 0, Math.min(255, borderAlpha));
            
            RenderUtil.Border.draw(
                    ctx,
                    drawX,
                    finalDrawY,
                    cardWidth,
                    cardHeight,
                    2.5f,
                    0.1f + currentSelect * 0.5f,
                    borderColor.getRGB()
            );

            // Фон с анимацией
            int bgAlpha = (int)(57 + currentHover * 30);
            RenderUtil.Round.draw(
                    ctx,
                    drawX,
                    finalDrawY,
                    cardWidth,
                    cardHeight,
                    2.5f,
                    themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1,1), Math.min(255, bgAlpha)) : 
                            RenderUtil.ColorUtil.getBackGroundColor(1, 1)
            );

            // Превью темы
            String name = theme.toString().toLowerCase();
            RenderUtil.Image.draw(
                    ctx,
                    Identifier.of("strange", "/textures/theme/" + name + ".png"),
                    drawX - 0.5f,
                    finalDrawY,
                    cardWidth + 1,
                    14f,
                    new Color(255, 255, 255, (int)(255 * (0.8f + currentHover * 0.2f)))
            );

            // Название темы с анимацией
            int textAlpha = (int)(RenderUtil.ColorUtil.getAlpha(RenderUtil.ColorUtil.getTextColor(1,1)) * (0.9f + currentHover * 0.1f));
            FontDraw.drawText(
                FontDraw.FontType.MEDIUM, 
                ctx, 
                theme.getName(), 
                drawX + 5, 
                finalDrawY + 20, 
                6, 
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1,1), textAlpha)
            );
            
            // Индикатор выбора
            if (currentSelect > 0.01f) {
                RenderUtil.Round.draw(
                    ctx,
                    drawX + cardWidth - 10,
                    finalDrawY + 4,
                    6,
                    6,
                    3f,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), (int)(255 * currentSelect))
                );
            }
        }
    }
}
