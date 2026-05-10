package ru.strange.client.ui.clickgui.render;

import net.minecraft.client.gui.DrawContext;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.impl.interfaces.ClickGui;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;

public class GuiRenderBackGround extends GuiScreen {
    public static void renderBackGround(DrawContext ctx) {
        boolean theme = ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK || ThemeManager.getTheme() == Theme.PURPLE || ThemeManager.getTheme() == Theme.PINK;

        ClickGui clickGui = ClickGui.getInstance();
        if (clickGui != null && clickGui.isGlassEnabled()) {
            int tint = 0xFFEAF2FA;
            RenderUtil.LiquidGlass.draw(ctx, x, y, width, height, 8f, tint,
                    clickGui.getGlassBlur(), clickGui.getGlassAlpha());
        } else {
            RenderUtil.Shadow.draw(ctx, x - 2, y - 2, width, height, 8, 12, new Color(0x40000000, true).getRGB());
            if (theme) RenderUtil.Blur.draw(ctx, x, y, width, height, 8, 20, new Color(255, 255, 255));
            RenderUtil.Round.draw(ctx, x, y, width, height, 8, theme ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 127) : RenderUtil.ColorUtil.getBackGroundColor(1, 1));
        }
        RenderUtil.Image.draw(ctx, Strange.id("icons/gui/logo.png"), x + 8, y + 8, 16, 16, RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1,1),204));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, Strange.name, x + 28, y + 15, 8, RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1,1), 204));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, ModLocalization.raw("FREE"), x + 28, y + 22, 5, RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1,1), 127));
    }
}
