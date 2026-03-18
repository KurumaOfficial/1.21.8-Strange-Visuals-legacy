package ru.strange.client.ui.clickgui.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.ListSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.other.KeyUtil;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.ArrayList;

public class GuiRenderModule extends GuiScreen {
    public static void renderModule(DrawContext ctx, double mouseX, double mouseY) {
        boolean themea = ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK || ThemeManager.getTheme() == Theme.PURPLE || ThemeManager.getTheme() == Theme.PINK;
        boolean blackTheme = ThemeManager.getTheme() == Theme.BLACK;

        float modulesX = x + 7;
        float modulesY = y + 64;
        float modulesWidth = 211;
        float modulesHeight = height - 64 - 7;

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, selectedCategories.getName(), x + 10, y + 42 + 13, 7, RenderUtil.ColorUtil.getTextColor(1,1));

        ctx.enableScissor(
                (int) modulesX,
                (int) modulesY,
                (int) (modulesX + modulesWidth),
                (int) (modulesY + modulesHeight)
        );

        scroll.update();

        float yDown = 0;
        float scrollY = scroll.getScroll();

        for (Module module : modules) {
            float up = calcUP(module);

            float drawY = modulesY + yDown + scrollY;

            RenderUtil.Border.draw(ctx, modulesX, drawY, 211, 26 + up, 5, 0.1f, blackTheme ? RenderUtil.ColorUtil.replAlpha(new Color(0xFFFFFFF).getRGB(), 80) : themea ? RenderUtil.ColorUtil.replAlpha(new Color(0x000000).getRGB(), 40) : RenderUtil.ColorUtil.replAlpha(new Color(0x000000).getRGB(), 80));

            RenderUtil.Round.draw(ctx, modulesX, drawY, 211, 26 + up, 5, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1,1), 64) : RenderUtil.ColorUtil.getBackGroundColor(1,1));

            RenderUtil.Image.draw(
                    ctx,
                    Identifier.of(Strange.rootRes, "/icons/gui/" + selectedCategories.toString().toLowerCase() + ".png"),
                    modulesX + 7,
                    drawY + 7,
                    12,
                    12,
                    RenderUtil.ColorUtil.getTextColor(1,1)
            );

            String displayText = module.getDisplayName();
            if (!module.binding) {
                String bindText = KeyUtil.getKey(module.bind);
                if (!bindText.equals("null")) {
                    displayText += " [" + bindText + "]";
                }
            }
            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    displayText,
                    modulesX + 25,
                    drawY + 12,
                    6,
                    RenderUtil.ColorUtil.getTextColor(1,1)
            );

            boolean enable = module.enable;
            float widthEnable = FontDraw.getWidth(
                    FontDraw.FontType.MEDIUM,
                    enable ? "ВКЛЮЧЕНО" : "ВЫКЛЮЧЕНО",
                    4
            );

            RenderUtil.Round.draw(
                    ctx,
                    modulesX + 23,
                    drawY + 14.5f,
                    widthEnable + 5.5f,
                    7,
                    3,
                    enable ? new Color(0x3300FF3A, true) : new Color(0x33FF0010, true)
            );

            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    enable ? "ВКЛЮЧЕНО" : "ВЫКЛЮЧЕНО",
                    modulesX + 26,
                    drawY + 19.5f,
                    4,
                    enable ? new Color(0x266E2Ce).getRGB() : new Color(0x920009).getRGB()
            );

            if (!module.getSettingsForGUI().isEmpty()) {
                float dotsX = modulesX + 201;
                RenderUtil.Round.draw(ctx, dotsX, drawY + 8, 3, 3, 1.5f,  RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, dotsX, drawY + 11.5F, 3, 3, 1.5f, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, dotsX, drawY + 15, 3, 3, 1.5f, RenderUtil.ColorUtil.getTextColor(1,1));
            }

            if (!module.getSettingsForGUI().isEmpty() && module.open) {
                java.util.List<Setting> settings1 = new ArrayList<>();
                java.util.List<Setting> settings2 = new ArrayList<>();

                for (int i = 0; i < module.getSettingsForGUI().size(); i++) {
                    Setting setting = module.getSettingsForGUI().get(i);
                    if (i % 2 == 0) {
                        settings1.add(setting);
                    } else {
                        settings2.add(setting);
                    }
                }
                GuiRenderSettings.renderSettings(ctx, settings1, x + 7, drawY + 26, mouseX, mouseY);
                GuiRenderSettings.renderSettings(ctx, settings2, x + 109, drawY + 26, mouseX, mouseY);
            }

            yDown += 30 + up;
        }

        float contentHeight = modules.size() * 30;
        for (Module m : modules) {
            contentHeight += calcUP(m);
        }
        scroll.setMax(contentHeight, modulesHeight);

        ctx.disableScissor();
        
        // Рендерим описание при наведении (поверх всего)
        renderModuleDescription(ctx, mouseX, mouseY, scrollY, modulesX, modulesY, modulesHeight);
    }
    
    private static float descriptionAlpha = 0f;
    private static Module lastHoveredModule = null;
    
    private static void renderModuleDescription(DrawContext ctx, double mouseX, double mouseY, float scrollY, float modulesX, float modulesY, float modulesHeight) {
        boolean themea = ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK || ThemeManager.getTheme() == Theme.PURPLE || ThemeManager.getTheme() == Theme.PINK;
        
        Module hoveredModule = null;
        float yDown = 0;
        
        for (Module module : modules) {
            float up = calcUP(module);
            float drawY = modulesY + yDown + scrollY;
            
            boolean isVisible = drawY + 26 > modulesY && drawY < modulesY + modulesHeight;
            
            if (isVisible && isHovered(mouseX, mouseY, modulesX, drawY, 211, 26)) {
                hoveredModule = module;
                break;
            }
            
            yDown += 30 + up;
        }
        
        // Плавная анимация появления/исчезновения
        if (hoveredModule != null && hoveredModule != lastHoveredModule) {
            descriptionAlpha = 0f;
        }
        
        if (hoveredModule != null) {
            descriptionAlpha = Math.min(1f, descriptionAlpha + 0.1f);
        } else {
            descriptionAlpha = Math.max(0f, descriptionAlpha - 0.15f);
        }
        
        lastHoveredModule = hoveredModule;
        
        if (descriptionAlpha > 0.01f && hoveredModule != null && !hoveredModule.description.isEmpty()) {
            String description = hoveredModule.description;
            float descWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, description, 6) + 12;
            float descHeight = 20;
            
            float descX = (float)mouseX + 10;
            float descY = (float)mouseY - descHeight - 5;
            
            // Проверяем границы экрана
            if (descX + descWidth > mc.getWindow().getScaledWidth()) {
                descX = (float)mouseX - descWidth - 10;
            }
            if (descY < 0) {
                descY = (float)mouseY + 10;
            }
            
            int bgAlpha = (int)(180 * descriptionAlpha);
            int borderAlpha = (int)(120 * descriptionAlpha);
            int textAlpha = (int)(255 * descriptionAlpha);
            
            RenderUtil.Round.draw(
                ctx,
                descX,
                descY,
                descWidth,
                descHeight,
                4f,
                themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1,1), bgAlpha) : 
                        RenderUtil.ColorUtil.replAlpha(new Color(20, 20, 20).getRGB(), bgAlpha)
            );
            
            RenderUtil.Border.draw(
                ctx,
                descX,
                descY,
                descWidth,
                descHeight,
                4f,
                0.5f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), borderAlpha)
            );
            
            FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                description,
                descX + 6,
                descY + 12,
                6,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1,1), textAlpha)
            );
        }
    }
}
