package ru.strange.client.ui.clickgui.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;
import ru.strange.client.utils.other.KeyUtil;
import ru.strange.client.utils.other.ServerRestrictionManager;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.List;

public class GuiRenderModule extends GuiScreen {
    private static float descriptionAlpha = 0.0F;
    private static Module lastHoveredModule = null;

    public static void renderModule(DrawContext ctx, double mouseX, double mouseY) {
        boolean themea = ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK || ThemeManager.getTheme() == Theme.PURPLE || ThemeManager.getTheme() == Theme.PINK;
        boolean blackTheme = ThemeManager.getTheme() == Theme.BLACK;

        ModulePanel modulePanel = modulePanelBounds();
        float modulesX = modulePanel.x();
        float modulesY = modulePanel.y();
        float modulesWidth = modulePanel.width();
        float modulesHeight = modulePanel.height();
        float moduleHeaderHeight = moduleHeaderHeight();

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, selectedCategories.getName(), x + 10, y + 42 + 13, 7, RenderUtil.ColorUtil.getTextColor(1,1));
        if (selectedCategories == Category.Theme) {
            return;
        }
        renderServerRuleBadge(ctx, modulesX, modulesWidth, themea, blackTheme);

        ctx.enableScissor(
                (int) modulesX,
                (int) modulesY,
                (int) (modulesX + modulesWidth),
                (int) (modulesY + modulesHeight)
        );

        scroll.update();

        float yDown = 0;
        float scrollY = scroll.getScroll();

        List<Module> visibleModules = visibleModules();

        for (Module module : visibleModules) {
            float up = calcUP(module);

            float drawY = modulesY + yDown + scrollY;

            RenderUtil.Border.draw(ctx, modulesX, drawY, 211, 26 + up, 5, 0.1f, blackTheme ? RenderUtil.ColorUtil.replAlpha(new Color(0xFFFFFFF).getRGB(), 80) : themea ? RenderUtil.ColorUtil.replAlpha(new Color(0x000000).getRGB(), 40) : RenderUtil.ColorUtil.replAlpha(new Color(0x000000).getRGB(), 80));

            RenderUtil.Round.draw(ctx, modulesX, drawY, 211, 26 + up, 5, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1,1), 64) : RenderUtil.ColorUtil.getBackGroundColor(1,1));

            RenderUtil.Image.draw(
                    ctx,
                        Strange.id("icons/gui/" + selectedCategories.toString().toLowerCase() + ".png"),
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
            String stateLabel = enable
                    ? GuiLocalization.tr("gui.status.enabled")
                    : GuiLocalization.tr("gui.status.disabled");
            float widthEnable = FontDraw.getWidth(
                    FontDraw.FontType.MEDIUM,
                    stateLabel,
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
                    stateLabel,
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
                SettingsColumns settingsColumns = splitSettingsByColumn(module);
                GuiRenderSettings.renderSettings(ctx, settingsColumns.left(), x + 7, drawY + moduleHeaderHeight, mouseX, mouseY);
                GuiRenderSettings.renderSettings(ctx, settingsColumns.right(), x + 109, drawY + moduleHeaderHeight, mouseX, mouseY);
            }

            yDown += moduleVerticalSpacing() + up;
        }

        float contentHeight = visibleModules.size() * moduleVerticalSpacing();
        for (Module m : visibleModules) {
            contentHeight += calcUP(m);
        }
        scroll.setMax(contentHeight, modulesHeight);

        if (visibleModules.isEmpty()) {
            renderEmptyState(ctx, modulesX, modulesY, modulesWidth, modulesHeight, themea, blackTheme);
        }

        ctx.disableScissor();

        renderModuleDescription(ctx, mouseX, mouseY, scrollY, modulesX, modulesY, modulesHeight, visibleModules);
    }

    private static void renderServerRuleBadge(DrawContext ctx, float modulesX, float modulesWidth, boolean themea, boolean blackTheme) {
        if (!ServerRestrictionManager.hasActiveProfile()) {
            return;
        }

        String badgeText = buildBadgeText();
        float badgeWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, badgeText, 5) + 12;
        float badgeHeight = 12;
        float badgeX = modulesX + modulesWidth - badgeWidth;
        float badgeY = y + 47;

        int backgroundColor = themea
                ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 96)
                : RenderUtil.ColorUtil.replAlpha(blackTheme ? new Color(16, 16, 16).getRGB() : new Color(24, 24, 24).getRGB(), 210);
        int borderColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 130);
        int accentColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 190);

        RenderUtil.Round.draw(ctx, badgeX, badgeY, badgeWidth, badgeHeight, 4, backgroundColor);
        RenderUtil.Border.draw(ctx, badgeX, badgeY, badgeWidth, badgeHeight, 4, 0.6f, borderColor);
        RenderUtil.Round.draw(ctx, badgeX + 4, badgeY + 4, 4, 4, 2, accentColor);
        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                badgeText,
                badgeX + 11,
                badgeY + 8,
                5,
                RenderUtil.ColorUtil.getTextColor(1, 1)
        );
    }

    private static String buildBadgeText() {
        String profileName = shortenText(ServerRestrictionManager.getActiveProfileName(), 20);
        int hiddenCount = ServerRestrictionManager.getHiddenModuleCount();
        return GuiLocalization.tr("gui.rule.badge", profileName, hiddenCount);
    }

    private static void renderEmptyState(DrawContext ctx, float modulesX, float modulesY, float modulesWidth, float modulesHeight, boolean themea, boolean blackTheme) {
        float cardWidth = Math.min(170, modulesWidth - 20);
        float cardHeight = 52;
        float cardX = modulesX + (modulesWidth - cardWidth) / 2f;
        float cardY = modulesY + Math.max(18, (modulesHeight - cardHeight) / 2f - 12);

        int backgroundColor = themea
                ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 96)
                : RenderUtil.ColorUtil.replAlpha(blackTheme ? new Color(12, 12, 12).getRGB() : new Color(20, 20, 20).getRGB(), 210);
        int borderColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 100);

        RenderUtil.Round.draw(ctx, cardX, cardY, cardWidth, cardHeight, 6, backgroundColor);
        RenderUtil.Border.draw(ctx, cardX, cardY, cardWidth, cardHeight, 6, 0.6f, borderColor);

        String title = ServerRestrictionManager.hasActiveProfile()
                ? GuiLocalization.tr("gui.empty.profile_title")
                : GuiLocalization.tr("gui.empty.category_title");
        String subtitle = ServerRestrictionManager.hasActiveProfile()
                ? GuiLocalization.tr("gui.empty.profile_subtitle")
                : GuiLocalization.tr("gui.empty.category_subtitle");

        FontDraw.drawCenter(
                FontDraw.FontType.MEDIUM,
                ctx,
                title,
                cardX + cardWidth / 2f,
                cardY + 18,
                6,
                RenderUtil.ColorUtil.getTextColor(1, 1),
                false
        );
        FontDraw.drawCenter(
                FontDraw.FontType.MEDIUM,
                ctx,
                subtitle,
                cardX + cardWidth / 2f,
                cardY + 31,
                5,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 180),
                false
        );
    }

    private static String shortenText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static void renderModuleDescription(DrawContext ctx, double mouseX, double mouseY, float scrollY, float modulesX, float modulesY, float modulesHeight, List<Module> visibleModules) {
        boolean themea = ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK || ThemeManager.getTheme() == Theme.PURPLE || ThemeManager.getTheme() == Theme.PINK;

        Module hoveredModule = null;
        float yDown = 0;

        for (Module module : visibleModules) {
            float up = calcUP(module);
            float drawY = modulesY + yDown + scrollY;

            boolean isVisible = drawY + moduleHeaderHeight() > modulesY && drawY < modulesY + modulesHeight;

            if (isVisible && isHovered(mouseX, mouseY, modulesX, drawY, modulePanelBounds().width(), moduleHeaderHeight())) {
                hoveredModule = module;
                break;
            }

            yDown += moduleVerticalSpacing() + up;
        }

        if (hoveredModule != null && hoveredModule != lastHoveredModule) {
            descriptionAlpha = 0f;
        }

        if (hoveredModule != null) {
            descriptionAlpha = Math.min(1f, descriptionAlpha + 0.1f);
        } else {
            descriptionAlpha = Math.max(0f, descriptionAlpha - 0.15f);
        }

        lastHoveredModule = hoveredModule;

        String description = hoveredModule == null ? "" : hoveredModule.getLocalizedDescription();
        if (descriptionAlpha > 0.01f && hoveredModule != null && !description.isBlank()) {
            float descWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, description, 6) + 12;
            float descHeight = 20;

            float descX = (float)mouseX + 10;
            float descY = (float)mouseY - descHeight - 5;

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
