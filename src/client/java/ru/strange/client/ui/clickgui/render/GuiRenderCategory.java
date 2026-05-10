package ru.strange.client.ui.clickgui.render;

import net.minecraft.client.gui.DrawContext;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;

public class GuiRenderCategory extends GuiScreen {
    public static void renderCategory(DrawContext ctx) {
        boolean theme = ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK;
        boolean themeTwo = ThemeManager.getTheme() == Theme.PURPLE || ThemeManager.getTheme() == Theme.PINK;
        boolean themeTree = ThemeManager.getTheme() == Theme.BLACK || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK;
        RenderUtil.Round.draw(ctx, x + 7, y + 30, 211, 12, 4, theme ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 127) : themeTwo ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 160) : RenderUtil.ColorUtil.getMainColor(1,1));
        float xGo = 0;
        for (Category category : categories) {
            if (category == selectedCategories) {
                RenderUtil.Border.draw(ctx, x + 10 + xGo, y + 31, FontDraw.getWidth(FontDraw.FontType.MEDIUM, category.getName(), 5) + 6, 10,3, 0.05f, theme ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 127) : themeTwo ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1,1), 127) : RenderUtil.ColorUtil.getBackGroundColor(1,1));
                RenderUtil.Round.draw(ctx, x + 10 + xGo, y + 31,  FontDraw.getWidth(FontDraw.FontType.MEDIUM, category.getName(), 5) + 6, 10,3, theme  ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 127) : themeTwo ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1,1), 127) : RenderUtil.ColorUtil.getBackGroundColor(1,1));
            }
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, category.getName(), x + 13.5f + xGo, y + 38f, 5, RenderUtil.ColorUtil.getTextColor(1,1));
            xGo += 9 + FontDraw.getWidth(FontDraw.FontType.MEDIUM, category.getName(), 5);
        }
    }
}
