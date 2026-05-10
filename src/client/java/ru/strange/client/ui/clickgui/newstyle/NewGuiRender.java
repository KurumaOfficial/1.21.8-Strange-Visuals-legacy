package ru.strange.client.ui.clickgui.newstyle;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.*;
import ru.strange.client.module.impl.interfaces.ClickGui;
import ru.strange.client.ui.clickgui.localization.GuiLanguage;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;
import ru.strange.client.utils.other.KeyUtil;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.List;

public class NewGuiRender {

    private static final int DAMAGE_TEXT_RGB = 0xFFFF9696;
    private static final int DARK_ADDITIONAL_RGB = new Color(24, 24, 27).getRGB();
    private static final int LIGHT_ADDITIONAL_RGB = new Color(200, 200, 206).getRGB();
    private static final Identifier PLAYER_ICON = Strange.id("icons/gui/player.png");
    private static final Identifier WORLD_ICON = Strange.id("icons/gui/world.png");
    private static final Identifier UTILITIES_ICON = Strange.id("icons/gui/utilities.png");
    private static final Identifier OTHER_ICON = Strange.id("icons/gui/other.png");
    private static final Identifier INTERFACE_ICON = Strange.id("icons/gui/interface.png");
    private static final Identifier THEME_ICON = Strange.id("icons/gui/theme.png");
    private static final Identifier BACK_ARROW_ICON = Strange.id("icons/gui/arrow2.png");
    private static final Identifier CHECK_ICON = Strange.id("icons/gui/check.png");
    private static final Identifier COLOR_PICKER_OVERLAY_ICON = Strange.id("icons/gui/c_bg.png");
    private static final Identifier HUE_BAR_ICON = Strange.id("icons/gui/hue.png");

    public static void render(DrawContext ctx, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        ThemeManager.update();
        NewGuiState.initPositions(screenWidth, screenHeight);
        long now = System.currentTimeMillis();

        float anim = easeOutQuart(NewGuiState.openAnimation);
        if (anim <= 0.001f) return;

        NewGuiState.searchAnimation = lerp(NewGuiState.searchAnimation, NewGuiState.searchFocused ? 1f : 0f, 0.16f);
        NewGuiState.searchAppendAnimation = lerp(NewGuiState.searchAppendAnimation,
            NewGuiState.searchQuery.isBlank() ? 0f : 1f, 0.16f);

        Theme currentTheme = ThemeManager.getTheme();
        boolean dark = currentTheme == Theme.BLACK || currentTheme == Theme.TRANSPARENT_BLACK;
        boolean light = currentTheme == Theme.WHITE || currentTheme == Theme.TRANSPARENT_WHITE;
        float shakeWave = (float) Math.sin(now / 18.0);
        boolean searchCursorVisible = ((now / 450L) & 1L) == 0L;
        boolean textCursorVisible = (now % 1000L) >= 500L;

        // Per-panel staggered animation: center panel (index 2) first, then neighbors (1,3), then outer (0,4)
        for (int i = 0; i < NewGuiState.CATEGORIES.length; i++) {
            float centerDist = Math.abs(i - 2); // 0 for center, 1 for adjacent, 2 for outer

            if (!NewGuiState.closing) {
                // Opening: stagger from center outward with smoother easing
                float staggerDelay = centerDist * 0.12f;
                float localRaw = Math.max(0f, Math.min(1f, (NewGuiState.openAnimation - staggerDelay) / (1f - staggerDelay)));
                NewGuiState.panelSizing[i] = easeOutQuart(localRaw);
            } else {
                // Closing: stagger from outer inward with smoother easing
                float staggerDelay = (2f - centerDist) * 0.10f;
                float closeProgress = 1f - NewGuiState.openAnimation; // 0→1 as closing progresses
                float localRaw = Math.max(0f, Math.min(1f, (closeProgress - staggerDelay) / (1f - staggerDelay)));
                NewGuiState.panelSizing[i] = 1f - easeOutQuart(localRaw);
            }
            if (NewGuiState.panelSizing[i] < 0.003f) NewGuiState.panelSizing[i] = 0f;
        }

        // Reset description each frame — modules will set it on hover
        NewGuiState.hoveredDescription = "";

        for (int i = 0; i < NewGuiState.CATEGORIES.length; i++) {
            float sizing = NewGuiState.panelSizing[i];
            if (sizing < 0.001f) continue;

            float slideOffset;
            float px;
            if (NewGuiState.closing) {
                // Closing: panels shrink and slide upward
                slideOffset = (1f - sizing) * -40f;
                px = NewGuiState.panelX[i];
            } else {
                // Opening: panels rise from below
                slideOffset = (1f - sizing) * 60f;
                px = NewGuiState.panelX[i];
            }
            float py = NewGuiState.panelY[i] + slideOffset;

            // Scale: 0.85→1.0 on open, 1.0→0.88 on close
            float scale = NewGuiState.closing
                    ? 0.88f + sizing * 0.12f
                    : 0.92f + sizing * 0.08f;
            float scaledW = NewGuiState.PANEL_WIDTH * scale;
            float scaledH = NewGuiState.PANEL_HEIGHT * scale;
            float scaleOffX = (NewGuiState.PANEL_WIDTH - scaledW) / 2f;
            float scaleOffY = (NewGuiState.PANEL_HEIGHT - scaledH) / 2f;

            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(px + scaleOffX + scaledW / 2f, py + scaleOffY + scaledH / 2f);
            ctx.getMatrices().scale(scale, scale);
            ctx.getMatrices().translate(-(px + NewGuiState.PANEL_WIDTH / 2f), -(py + NewGuiState.PANEL_HEIGHT / 2f));

            renderPanel(ctx, i, mouseX, mouseY, Math.min(sizing, 1f), px, anim, dark, light, shakeWave, textCursorVisible);

            ctx.getMatrices().popMatrix();
        }

        // Module description (ported from DropDownScreen: descText.pos(width/2, height/2 - 150))
        renderDescription(ctx, anim, screenWidth, screenHeight);

        // Theme bar + search field adaptation for current base
        renderThemeBar(ctx, mouseX, mouseY, anim, screenWidth, light);
        renderSearch(ctx, anim, dark, searchCursorVisible);
        renderLanguageSwitch(ctx, mouseX, mouseY, anim, dark);
    }

    private static void renderPanel(DrawContext ctx, int idx, int mouseX, int mouseY,
                                     float sizing, float px, float anim,
                                     boolean dark, boolean light, float shakeWave, boolean textCursorVisible) {
        float py = NewGuiState.panelY[idx];
        float pw = NewGuiState.PANEL_WIDTH;
        float ph = NewGuiState.PANEL_HEIGHT;
        Category cat = NewGuiState.CATEGORIES[idx];
        float headerH = NewGuiState.HEADER_HEIGHT; // 24
        float sepH = NewGuiState.SEPARATOR_HEIGHT;  // 4

        // Alpha from sizing (ported: closing ? 2-sizing : sizing — simplified since sizing is 0..1)
        float panelAlpha = sizing;

        drawSurfaceCard(ctx, px, py, pw, ph, 12f, panelAlpha, dark, 1.0f);

        // Title text (ported: SEMIBOLD 9pt, leftPadding=10, centered in headerH=24 with +0.5)
        int textAlpha = (int)(panelAlpha * 255);
        int textColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), textAlpha);
        float titleY = centeredTextY(py, headerH, FontDraw.FontType.SEMIBOLD, 8) + 0.5f;
        FontDraw.drawText(FontDraw.FontType.SEMIBOLD, ctx, cat.getName(), px + 10, titleY, 8, textColor);

        // Category icon (ported: 8x8, rightPadding=10, centered in headerH=24 with +0.5)
        try {
            float iconSz = 10f;
            float iconY = centeredContentY(py, headerH, iconSz) + 0.5f;
            RenderUtil.Image.draw(ctx, categoryIcon(cat),
                    px + pw - 10 - iconSz, iconY, iconSz, iconSz,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(panelAlpha * 255)));
        } catch (Exception ignored) {}

        // Header separator (ported: 4px rect at y+headerH, separatorColor — drawn subtly)
        int sepColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(panelAlpha * 20));
        RenderUtil.Rect.draw(ctx, px, py + headerH, pw, sepH, sepColor);

        // Swap animation (ported from MenuPanel: swapping = Animation(500, BAKEK_PAGES))
        if (NewGuiState.selectedModule[idx] != null) {
            NewGuiState.lastSelectedModule[idx] = NewGuiState.selectedModule[idx];
        }
        float swapTarget = NewGuiState.selectedModule[idx] != null ? 1f : 0f;
        NewGuiState.swapAnimation[idx] = NewGuiState.smoothLerp(NewGuiState.swapAnimation[idx], swapTarget, 0.11f);
        if (Math.abs(NewGuiState.swapAnimation[idx] - swapTarget) < 0.003f) {
            NewGuiState.swapAnimation[idx] = swapTarget;
        }
        float swap = easeInOutCubic(NewGuiState.swapAnimation[idx]);

        NewGuiState.modulesScroll[idx].update();
        NewGuiState.settingsScroll[idx].update();

        float contentTop = py + headerH + sepH;
        float contentH = ph - headerH - sepH;

        // ===== MODULE LIST VIEW (ported from MenuPanel.renderComponent + ModuleComponent) =====
        if (swap < 1f) {
            // Ported: float x = this.x + -this.width * swapping.getValue()
            float modListX = px + (-pw * swap);
            // Ported: RenderSystem.setShaderColor(1,1,1, alpha * (1-swapping))
            float modListAlpha = panelAlpha * (1f - swap);

            ctx.enableScissor((int) px, (int) contentTop, (int)(px + pw), (int)(py + ph));

            float scrollY = NewGuiState.modulesScroll[idx].getScroll();
            List<Module> modules = NewGuiState.getVisibleModules(cat);
            float offset = 0f;

            for (int mi = 0; mi < modules.size(); mi++) {
                Module module = modules.get(mi);
                float modY = contentTop + offset + scrollY;
                float modX = modListX;
                float modH = NewGuiState.MODULE_HEIGHT; // 20 (ported from ModuleComponent: height=20)

                // Hover animation (ported: Animation(300, FIGMA_EASE_IN_OUT))
                boolean hovered = mouseX >= px + 4 && mouseX <= px + pw - 4
                        && mouseY >= modY && mouseY <= modY + modH
                        && mouseY >= contentTop && mouseY <= py + ph
                        && swap < 0.5f;
                float currentHover = lerp(NewGuiState.getHover(module), hovered ? 1f : 0f, 0.15f);
                NewGuiState.setHover(module, currentHover);

                // Description on hover (ported from ModuleComponent.renderComponent:
                // dropDownScreen.setDesc(module.getDescription()))
                if (hovered && module.description != null && !module.description.isEmpty()) {
                    NewGuiState.hoveredDescription = module.getLocalizedDescription();
                }

                // Enable animation (ported: Animation(300, FIGMA_EASE_IN_OUT))
                float enableTarget = module.enable ? 1f : 0f;
                float currentEnable = lerp(NewGuiState.getEnable(module), enableTarget, 0.12f);
                NewGuiState.setEnable(module, currentEnable);

                float blockAnim = lerp(NewGuiState.getBlocking(module), 0f, 0.10f);
                NewGuiState.setBlocking(module, blockAnim);
                float shakeAnim = lerp(NewGuiState.getShake(module), 0f, 0.16f);
                NewGuiState.setShake(module, shakeAnim);
                float shakeOffset = shakeWave * 2.2f * shakeAnim;

                // Module name (ported from ModuleComponent.drawRegular8:
                // nameLeftPadding = 10 + 2*enableAnim, y + middleOfBox(nameH, 20) - 0.5,
                // alpha = 0.75 + 0.25*enable + 0.25*hover)
                String name = module.binding
                    ? (module.bind == -1 ? ModLocalization.tr("common.press_key") : ModLocalization.raw("Кнопка") + ": " + KeyUtil.getKey(module.bind).toUpperCase())
                        : module.getDisplayName();
                float nameLeftPadding = 10f + 2f * currentEnable + shakeOffset;
                float nameAlphaF = (0.75f + 0.25f * currentEnable + 0.25f * currentHover) * modListAlpha;
                int baseText = RenderUtil.ColorUtil.getTextColor(1, 1);
                int tintedText = mixColor(baseText, DAMAGE_TEXT_RGB, blockAnim);
                int nameColor = RenderUtil.ColorUtil.replAlpha(tintedText, (int)(nameAlphaF * 255));
                float nameY = centeredTextY(modY, modH, FontDraw.FontType.MEDIUM, 7) - 0.5f;
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, name,
                        modX + nameLeftPadding, nameY, 7, nameColor);

                // Enable checkmark icon (ported from ModuleComponent.drawIcons:
                // x+width-15-enableAnim*2, y+7, 6x6, alpha = textColor * (0.1+0.9*enableAnim))
                if (currentEnable > 0.01f) {
                    float iconAlpha = (0.1f + 0.9f * currentEnable) * modListAlpha;
                    int checkColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(iconAlpha * 255));
                    float checkX = modX + pw - 15f - currentEnable * 2f;
                    float checkY = modY + 7f;
                    drawCheckIcon(ctx, checkX, checkY, 6.0f, checkColor);
                }

                // Module separator (ported from ModuleComponent.drawSplit:
                // 0.5px at y+height, textColor alpha 0.02)
                int splitColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(modListAlpha * 255 * 0.02f));
                RenderUtil.Rect.draw(ctx, modX, modY + modH, pw, 0.5f, splitColor);

                offset += modH;
            }

            NewGuiState.modulesScroll[idx].setMax(offset, contentH);
            ctx.disableScissor();
        }

        // ===== SETTINGS VIEW (ported from MenuPanel: settings swap) =====
        if (swap > 0f && NewGuiState.lastSelectedModule[idx] != null) {
            Module selModule = NewGuiState.lastSelectedModule[idx];
            // Ported: float x = this.x + this.width * (1-swapping)
            float settX = px + pw * (1f - swap);
            float settAlpha = panelAlpha * swap;

            float backAreaY = contentTop;
            ctx.enableScissor((int) px, (int) py, (int)(px + pw), (int)(py + ph));

                // Back arrow layout is configurable in NewGuiState for quick pixel tuning.
            float arrowSize = NewGuiState.BACK_ARROW_SIZE;
            int arrowColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(settAlpha * 255));
            float arrowX = settX + NewGuiState.BACK_ARROW_OFFSET_LEFT;
            float arrowY = centeredContentY(backAreaY, headerH, arrowSize) + NewGuiState.BACK_ARROW_OFFSET_TOP;
                RenderUtil.Image.draw(ctx, BACK_ARROW_ICON, arrowX, arrowY, arrowSize, arrowSize,
                    RenderUtil.ColorUtil.getColor(arrowColor));

            // Module name spacing follows the arrow constants so both can be tuned together.
            int modNameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(settAlpha * 255));
            float modNameY = centeredTextY(backAreaY, headerH, FontDraw.FontType.MEDIUM, 7) - 0.5f;
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, selModule.getDisplayName(),
                        arrowX + arrowSize + NewGuiState.BACK_TITLE_GAP, modNameY, 7, modNameColor);

            ctx.disableScissor();

            // Settings list (ported: scissor y+headerH*2+sepH to y+height-0.5)
            float settListY = contentTop + headerH;
            float settListH = ph - headerH * 2f - sepH - 0.5f;
            ctx.enableScissor((int) px, (int) settListY, (int)(px + pw), (int)(settListY + settListH));

            float scrollY = NewGuiState.settingsScroll[idx].getScroll();
            float settOffset = 0f;
            List<Setting> settings = selModule.getSettingsForGUI();

            for (int si = 0; si < settings.size(); si++) {
                Setting setting = settings.get(si);
                float settH = NewGuiState.measureSettingHeight(setting);
                if (settH <= 0) continue;

                float sy = settListY + settOffset + scrollY;
                renderSetting(ctx, setting, settX, sy, pw, mouseX, mouseY, settAlpha, dark, light, textCursorVisible);
                settOffset += settH;
            }

            NewGuiState.settingsScroll[idx].setMax(settOffset, settListH);
            ctx.disableScissor();
        }
    }

    // ===== MODULE DESCRIPTION (ported from DropDownScreen: descText at width/2, height/2-150) =====
    private static void renderDescription(DrawContext ctx, float anim, int screenWidth, int screenHeight) {
        boolean hasDesc = !NewGuiState.hoveredDescription.isEmpty();
        float descTarget = hasDesc ? 1f : 0f;
        NewGuiState.descriptionAlpha = lerp(NewGuiState.descriptionAlpha, descTarget, 0.12f);

        // Slide-down animation: starts from slightly above target, slides down
        float offsetTarget = hasDesc ? 0f : -8f;
        NewGuiState.descriptionOffsetY = lerp(NewGuiState.descriptionOffsetY, offsetTarget, 0.10f);

        if (NewGuiState.descriptionAlpha < 0.01f) {
            NewGuiState.descriptionOffsetY = -8f; // reset for next appearance
            return;
        }

        String desc = NewGuiState.hoveredDescription;
        if (desc.isEmpty() || desc.contains(".description")) return;

        float alpha = NewGuiState.descriptionAlpha * anim;
        int descColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(alpha * 255));
        float descY = screenHeight / 2f - 150f + NewGuiState.descriptionOffsetY;
        FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, desc, screenWidth / 2f, descY, 8, descColor, false);
    }

    // ===== THEME SELECTOR BAR =====
    private static void renderThemeBar(DrawContext ctx, int mouseX, int mouseY, float anim,
                                        int screenWidth, boolean light) {
        Theme[] themes = Theme.values();
        float dotSz = 14f;
        float dotGap = 8f;
        float totalW = themes.length * dotSz + (themes.length - 1) * dotGap;
        float barX = (screenWidth - totalW) / 2f;
        float barY = NewGuiState.getThemeBarY();

        for (int i = 0; i < themes.length; i++) {
            Theme theme = themes[i];
            float dx = barX + i * (dotSz + dotGap);
            float dy = barY;

            boolean hovered = mouseX >= dx - 2 && mouseX <= dx + dotSz + 2
                    && mouseY >= dy - 2 && mouseY <= dy + dotSz + 2;
            float hoverAnim = lerp(NewGuiState.getThemeHover(theme), hovered ? 1f : 0f, 0.15f);
            NewGuiState.setThemeHover(theme, hoverAnim);

            float scale = 1f + hoverAnim * 0.12f;
            float sz = dotSz * scale;
            float off = (dotSz - sz) / 2f;

            RenderUtil.Round.draw(ctx, dx + off, dy + off, sz, sz, sz / 2f,
                    RenderUtil.ColorUtil.replAlpha(theme.getMain().getRGB(), (int)(anim * 255)));

            boolean selected = ThemeManager.getTheme() == theme;
            float selAnim = lerp(NewGuiState.getThemeSelect(theme), selected ? 1f : 0f, 0.12f);
            NewGuiState.setThemeSelect(theme, selAnim);
            if (selAnim > 0.01f) {
                int ringA = (int)(selAnim * anim * 200);
                float ringOff = 2f;
                float ringSz = sz + ringOff * 2;
                int ringColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), ringA);
                RenderUtil.Border.draw(ctx, dx + off - ringOff, dy + off - ringOff, ringSz, ringSz, ringSz / 2f, 0.3f, ringColor);
            }
        }
    }

    private static void renderSearch(DrawContext ctx, float anim, boolean dark, boolean cursorVisible) {
        float searchAlpha = anim * NewGuiState.searchAnimation;
        float searchX = NewGuiState.getSearchX();
        float searchY = NewGuiState.getSearchY();
        float searchW = NewGuiState.SEARCH_WIDTH;
        float searchH = NewGuiState.SEARCH_HEIGHT;
        float searchTextH = FontDraw.getHeight(FontDraw.FontType.MEDIUM, 7);
        float searchTextY = centeredTextY(searchY, searchH, FontDraw.FontType.MEDIUM, 7) - 2.5f;
        float textStartX = searchX + 10f;

        if (searchAlpha > 0.01f) {
            drawSurfaceCard(ctx, searchX, searchY, searchW, searchH, 6f, searchAlpha, dark, 0.82f);

            String query = NewGuiState.searchQuery;
            boolean showPlaceholder = query.isBlank();
            String displayText = showPlaceholder ? text("Поиск", "Search") : query;
            int textColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1),
                (int) ((showPlaceholder ? 110 : 220) * searchAlpha));
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, displayText, textStartX,
                searchTextY, 7, textColor);

            if (NewGuiState.searchFocused && cursorVisible) {
                float queryWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, query, 7);
                float cursorX = textStartX + queryWidth;
                RenderUtil.Rect.draw(ctx, cursorX + 1f, searchY + 5f, 0.8f, searchH - 10f,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (180 * searchAlpha)));
            }

            float appendAlpha = anim * NewGuiState.searchAppendAnimation;
            if (appendAlpha > 0.01f) {
                int hintColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (120 * appendAlpha));
                float hintBaseY = NewGuiState.getThemeBarY() - 18f;
                FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, text("TAB или Ctrl+F - поиск", "TAB or Ctrl+F - search"),
                    NewGuiState.currentScreenWidth / 2f, hintBaseY, 7, hintColor, false);
                FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, text("Enter - закрыть поиск", "Enter - close search"),
                    NewGuiState.currentScreenWidth / 2f, hintBaseY + 10f, 7, hintColor, false);
            }
            return;
        } else {
            // Clear search when not focused (ported from DropDownScreen: searchField.clear())
            if (!NewGuiState.searchQuery.isBlank()) {
                NewGuiState.searchQuery = "";
            }
        }

        // Tooltip always visible at bottom when search not focused
        float tooltipAlpha = anim * (1f - NewGuiState.searchAnimation);
        if (tooltipAlpha > 0.01f) {
            int tooltipColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (120 * tooltipAlpha));
            float tooltipY = NewGuiState.currentScreenHeight - 12f;
            FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, text("TAB или Ctrl+F - поиск", "TAB or Ctrl+F - search"),
                NewGuiState.currentScreenWidth / 2f, tooltipY, 7, tooltipColor, false);
        }
    }

    private static float px(float v) {
        return (float) Math.round(v);
    }

    private static void renderLanguageSwitch(DrawContext ctx, int mouseX, int mouseY, float anim, boolean dark) {
        final float x = px(NewGuiState.getLanguageSwitchX());
        final float y = px(NewGuiState.getLanguageSwitchY());

        final float w = NewGuiState.LANGUAGE_SWITCH_WIDTH;
        final float h = NewGuiState.LANGUAGE_SWITCH_HEIGHT;
        final float gap = NewGuiState.LANGUAGE_SWITCH_SEGMENT_GAP;
        final float pillY = px(NewGuiState.getLanguagePillY());
        final float pillH = px(NewGuiState.getLanguagePillHeight());
        final float segW = px(NewGuiState.getLanguageSegmentWidth());
        final float ruX = px(NewGuiState.getLanguageRuX());
        final float enX = px(NewGuiState.getLanguageEnX());

        final boolean hoverRu =
                mouseX >= ruX && mouseX <= ruX + segW &&
                        mouseY >= pillY && mouseY <= pillY + pillH;

        final boolean hoverEn =
                mouseX >= enX && mouseX <= enX + segW &&
                        mouseY >= pillY && mouseY <= pillY + pillH;

        NewGuiState.languageHoverRu = lerp(NewGuiState.languageHoverRu, hoverRu ? 1f : 0f, 0.18f);
        NewGuiState.languageHoverEn = lerp(NewGuiState.languageHoverEn, hoverEn ? 1f : 0f, 0.18f);

        drawSurfaceCard(ctx, x, y, w, h, 8f, anim * 0.95f, dark, 0.78f);

        final boolean ruSelected = GuiLocalization.currentLanguage() == GuiLanguage.RU;
        final float pillTarget = ruSelected ? 0f : 1f;

        if (!NewGuiState.languageSelectionInitialized) {
            NewGuiState.languageSelectionAnimation = pillTarget;
            NewGuiState.languageSelectionInitialized = true;
        }
        NewGuiState.languageSelectionAnimation = lerp(NewGuiState.languageSelectionAnimation, pillTarget, 0.14f);

        final float pillX = px(ruX + (segW + gap) * easeOutCubic(NewGuiState.languageSelectionAnimation));

        final int pillColor = themeAccentColor((int) (anim * 190));

        RenderUtil.Round.draw(
                ctx,
                pillX,
                pillY,
                segW,
                pillH,
                pillH / 2f,
                pillColor
        );

        final int baseText = RenderUtil.ColorUtil.getTextColor(1, 1);

        final int ruColor = ruSelected
            ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int) (anim * 255))
            : RenderUtil.ColorUtil.replAlpha(baseText, (int) (anim * (160 + 30 * NewGuiState.languageHoverRu)));
        final int enColor = !ruSelected
            ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int) (anim * 255))
            : RenderUtil.ColorUtil.replAlpha(baseText, (int) (anim * (160 + 30 * NewGuiState.languageHoverEn)));

        final float langTextY = px(centeredTextY(pillY, pillH, FontDraw.FontType.SEMIBOLD, 3)
            + NewGuiState.LANGUAGE_SWITCH_TEXT_Y_OFFSET);

        final float ruCx = px(ruX + segW / 2f);
        final float enCx = px(enX + segW / 2f);

        FontDraw.drawCenter(FontDraw.FontType.SEMIBOLD, ctx, "RU", ruCx, langTextY, 6, ruColor, false);
        FontDraw.drawCenter(FontDraw.FontType.SEMIBOLD, ctx, "EN", enCx, langTextY, 6, enColor, false);
    }

    // ========== SETTING RENDERERS ==========

    private static void renderSetting(DrawContext ctx, Setting setting, float x, float y, float w,
                                       int mouseX, int mouseY, float anim, boolean dark, boolean light, boolean textCursorVisible) {
        if (setting instanceof BooleanSetting s) {
            if (s.hidden.get()) return;
            renderBoolean(ctx, s, x, y, w, anim, dark);
        } else if (setting instanceof SliderSetting s) {
            if (s.hidden.get()) return;
            renderSlider(ctx, s, x, y, w, mouseX, anim, dark);
        } else if (setting instanceof ModeSetting s) {
            if (s.hidden.get()) return;
            renderMode(ctx, s, x, y, w, anim);
        } else if (setting instanceof BindSettings s) {
            if (s.hidden.get()) return;
            renderBind(ctx, s, x, y, w, anim);
        } else if (setting instanceof StringSetting s) {
            if (s.hidden.get()) return;
            renderString(ctx, s, x, y, w, anim, textCursorVisible);
        } else if (setting instanceof ButtonSetting s) {
            if (s.hidden.get()) return;
            renderButton(ctx, s, x, y, w, anim);
        } else if (setting instanceof HueSetting s) {
            if (s.hidden.get()) return;
            renderColor(ctx, s, x, y, w, mouseX, mouseY, anim);
        } else if (setting instanceof MultiBooleanSetting s) {
            if (s.hidden.get()) return;
            renderMultiBoolean(ctx, s, x, y, w, anim);
        } else if (setting instanceof ListSetting s) {
            if (s.hidden.get()) return;
            renderList(ctx, s, x, y, w, anim);
        }

        // Setting separator (ported from all setting components drawSplit:
        // 0.5px rect at y+height, textColor alpha 5.1/255 ≈ 0.02)
        float settH = NewGuiState.measureSettingHeight(setting);
        if (settH > 0) {
            int splitColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 255 * 0.02f));
            RenderUtil.Rect.draw(ctx, x, y + settH, w, 0.5f, splitColor);
        }
    }

    // BooleanSetting: height=18 (ported from BooleanSettingComponent)
    // Toggle: 13x8, knob 6x6, ON=purple(151,71,255), OFF=additionalColor
    private static void renderBoolean(DrawContext ctx, BooleanSetting s, float x, float y, float w,
                                       float anim, boolean dark) {
        // Enable animation (ported: Animation(300, BAKEK))
        float toggleTarget = s.get() ? 1f : 0f;
        float toggleAnim = lerp(NewGuiState.getSettingToggle(s), toggleTarget, 0.12f);
        NewGuiState.setSettingToggle(s, toggleAnim);

        // Name (ported: REGULAR 8pt, leftPadding=10, centered in 19px - 0.5,
        // alpha = 0.75+0.25*enable+0.25*hover)
        float nameAlpha = (0.75f + 0.25f * toggleAnim) * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        float nameY = settingHeaderTextY(y);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, s.getDisplayName(),
            x + 10, nameY, 7, nameColor);

        // Toggle background is shifted 1px down so its optical center matches the text row.
        // ON: ColorRGBA(151,71,255), OFF: theme.additionalColor ≈ dark(24,24,27))
        float checkWidth = 13f;
        float checkHeight = 8f;
        float toggleX = px(x + w - checkWidth - 9f);
        float toggleY = px(y + 9f);

        int offColorRgb = dark ? DARK_ADDITIONAL_RGB : LIGHT_ADDITIONAL_RGB;
        int onColorRgb = RenderUtil.ColorUtil.getTextColor(1, 1);
        int mixedColor = mixColor(offColorRgb, onColorRgb, toggleAnim);
        float toggleBgAlpha = 1f;
        int toggleBgColor = RenderUtil.ColorUtil.replAlpha(mixedColor, (int)(anim * toggleBgAlpha * 255));
        RenderUtil.Round.draw(ctx, toggleX, toggleY, checkWidth, checkHeight, 3f, toggleBgColor);

        // Knob follows the same 1px vertical correction as the toggle background.
        // white, alpha = circleOpacity (enabled?1:0.75))
        float circleOpacity = s.get() ? 1f : 0.75f;
        float knobX = px(toggleX + 1f + 5f * toggleAnim);
        float knobY = px(y + 10f);
        int knobAlpha = (int)(anim * circleOpacity * 255);
        int knobColor = s.get()
            ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), knobAlpha)
            : RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), knobAlpha);
        RenderUtil.Round.draw(ctx, knobX, knobY, 6f, 6f, 3f,
            knobColor);
    }

    // SliderSetting: height=29 (ported from SliderSettingComponent)
    // Track 2px, accent fill, 6x6 white knob
    private static void renderSlider(DrawContext ctx, SliderSetting s, float x, float y, float w,
                                      int mouseX, float anim, boolean dark) {
        // Ported layout: float lx = this.x + 9, ly = this.y + 2, lw = this.width - 18
        float sliderX = x + 9f;
        float sliderW = w - 18f;

        // Name (ported: REGULAR 8pt at x+10, y+2+11-fontHeight, alpha=0.75+0.25*hover)
        float nameAlpha = 0.75f * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, s.getDisplayName(),
            x + 10, centeredTextY(y + 2f, 11f, FontDraw.FontType.MEDIUM, 7), 7, nameColor);

        // Value text (ported: REGULAR 7pt, right-aligned, same Y)
        String value = s.percent ? Math.round(s.get() * 100f) + "%" : trimSliderValue(s.get());
        float valW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, value, 6);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, value,
            sliderX + sliderW - valW, centeredTextY(y + 2f, 11f, FontDraw.FontType.MEDIUM, 6), 6,
            RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255)));

        // Track (ported: at y+2+height-12=y+19, width, 2px)
        float trackY = px(y + 19f);
        float trackH = 2f;
        float trackAlpha = 0.7f * anim;
        int trackColor = additionalColor(dark, (int)(trackAlpha * 255));
        RenderUtil.Round.draw(ctx, sliderX, trackY, sliderW, trackH, 0.25f, trackColor);

        // Drag logic
        if (s.sliding) {
            float pct = (float)(mouseX - sliderX) / sliderW;
            pct = Math.max(0f, Math.min(1f, pct));
            s.current = (float) ru.strange.client.utils.math.MathHelper.round(
                    s.minimum + pct * (s.maximum - s.minimum), s.increment);
            s.current = ru.strange.client.utils.math.MathHelper.clamp(s.current, s.minimum, s.maximum);
            s.triggerDeferredAutoSave();
        }

        float targetW = ((s.current - s.minimum) / (s.maximum - s.minimum)) * sliderW;
        s.sliderWidth = lerp(s.sliderWidth, targetW, 0.2f);

        // Fill (ported: Colors.ACCENT = purple)
        int fillColor = themeAccentColor((int)(anim * 255));
        RenderUtil.Round.draw(ctx, sliderX, trackY, Math.max(trackH, s.sliderWidth), trackH, 0.25f, fillColor);

        // Knob (ported: 6x6 white circle at fill end, y+height-14=y+17 from y+2 base → y+17)
        float knobSz = 6f;
        // Clamp knob position to stay within slider bounds
        float knobOffset = Math.max(0f, Math.min(sliderW, s.sliderWidth));
        float knobX = px(sliderX + knobOffset - knobSz / 2f);
        float knobY = px(y + 17f);
        RenderUtil.Round.draw(ctx, knobX, knobY, knobSz, knobSz, 3f,
            RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 255)));
    }

    // ModeSetting: height = 31 + modes*12 (ported from ModeSettingComponent)
    // Box with options, checkmark for selected
    private static void renderMode(DrawContext ctx, ModeSetting s, float x, float y, float w,
                                    float anim) {
        // Name (ported: REGULAR 8pt, x+10, y+middleOfBox(fontH, 19), alpha=0.75+0.25*hover)
        float nameAlpha = 0.75f * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, s.getDisplayName(),
            x + 10, settingHeaderTextY(y), 7, nameColor);

        // Box (ported: x+9-1=x+8, y+1+17=y+18, w-18+2=w-16, 8+modes*12, radius 6,
        // backgroundColor alpha 76.5/255 ≈ 30%)
        float boxX = x + 8f;
        float boxY = y + 18f;
        float boxW = w - 16f;
        float boxH = 8f + s.modes.size() * 12f;
        int boxBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(anim * 76));
        RenderUtil.Round.draw(ctx, boxX, boxY, boxW, boxH, 6, boxBg);

        // Options (ported: REGULAR 7pt at x+9+7=x+16, y+1+24.5=y+25.5 + offset)
        float offset = 0f;
        for (int i = 0; i < s.modes.size(); i++) {
            String modeName = ModLocalization.raw(s.modes.get(i));
            boolean selected = s.modes.get(i).equals(s.currentMode);

            // Alpha (ported: 0.75+0.25*hover+0.25*active)
            float optAlpha = (0.75f + (selected ? 0.25f : 0f)) * anim;
            int optColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(optAlpha * 255));

            // Text at y+25.5+offset (ported from reference)
            float optionTextY = settingOptionTextY(y, offset);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, modeName,
                x + 16f, optionTextY, 6, optColor);

            // Checkmark icon for selected, vertically centered in the 12px row
            if (selected) {
                float iconAlpha = anim;
                int checkColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(iconAlpha * 255));
                drawCheckIcon(ctx, x + w - 20f, y + 20f + offset + 3f, 6.0f, checkColor);
            }

            offset += 12f;
        }
    }

    // BindSettings: height=19 (ported from BindSettingComponent)
    // Badge with key text, additionalColor bg
    private static void renderBind(DrawContext ctx, BindSettings s, float x, float y, float w,
                                    float anim) {
        // Name (ported: REGULAR 8pt, x+10, centered in 19px, alpha=0.75+0.25*hover)
        float nameAlpha = 0.75f * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, s.getDisplayName(),
            x + 10, settingHeaderTextY(y), 7, nameColor);

        // Key badge is shifted 1px down so it sits on the same visual baseline as the label.
        String key = s.active ? "..." : KeyUtil.getKey(s.get()).toUpperCase();
        float keyW = Math.max(22f, FontDraw.getWidth(FontDraw.FontType.MEDIUM, key, 6) + 7f);
        float badgeH = 12f;
        float badgeX = px(x + w - 9f - keyW);
        float badgeY = px(y + 7f);

        boolean dark = ThemeManager.getTheme() == Theme.BLACK || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK;
        int badgeBg = additionalColor(dark, (int)(anim * 255));
        RenderUtil.Round.draw(ctx, badgeX, badgeY, keyW, badgeH, 3f, badgeBg);

        // Key text follows the same 1px vertical correction as the badge.
        int keyColor;
        if (s.active) {
            keyColor = themeAccentColor((int)(anim * 255));
        } else {
            keyColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        }
        FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, key,
            px(badgeX + keyW / 2f), px(centeredTextY(badgeY, badgeH, FontDraw.FontType.MEDIUM, 3) + 0.5f), 6, keyColor, false);
    }

    // StringSetting: height=35 (ported from StringSettingComponent)
    // Text field with backgroundColor bg
    private static void renderString(DrawContext ctx, StringSetting s, float x, float y, float w,
                                      float anim, boolean cursorVisible) {
        // Name (ported: REGULAR 8pt, x+10, centered in 19px - 0.5, alpha=0.75+0.25*hover)
        float nameAlpha = 0.75f * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, s.getDisplayName(),
            x + 10, settingHeaderTextY(y), 7, nameColor);

        // Field (ported: x+8, y+15, w-16, height-20=15, radius 4, backgroundColor alpha 76.5)
        float fieldX = x + 8f;
        float fieldY = y + 15f;
        float fieldW = w - 16f;
        float fieldH = 15f;
        int fieldBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(76 * anim));
        RenderUtil.Round.draw(ctx, fieldX, fieldY, fieldW, fieldH, 4f, fieldBg);

        // Text with cursor
        String text = s.get() + (s.active && cursorVisible ? "|" : "");
        int fTextColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 220));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, text,
            fieldX + 4f, centeredTextY(fieldY, fieldH, FontDraw.FontType.MEDIUM, 6) - 0.5f, 6, fTextColor);
    }

    // ButtonSetting: height=24 (ported from ButtonSettingComponent)
    // Button area with hover-reactive alpha
    private static void renderButton(DrawContext ctx, ButtonSetting s, float x, float y, float w,
                                      float anim) {
        // Button bg (ported: x+7, y+4, w-14, height-7=17, radius 6,
        // backgroundColor alpha 255*(0.3+0.2*hover))
        float btnX = x + 7f;
        float btnY = y + 4f;
        float btnW = w - 14f;
        float btnH = 17f;
        float bgAlpha = 0.3f; // no hover tracking in render, just base
        int btnBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(255 * bgAlpha * anim));
        RenderUtil.Round.draw(ctx, btnX, btnY, btnW, btnH, 6f, btnBg);

        // Name centered (ported: REGULAR 8pt, centered, alpha=0.75+0.25*hover)
        float nameAlpha = 0.75f * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, ModLocalization.raw(s.getActionLabel()),
            btnX + btnW / 2f, centeredTextY(btnY, btnH, FontDraw.FontType.MEDIUM, 7) - 0.5f, 7, nameColor, false);
    }

    // HueSetting: height=18 + (opened ? 78 : 0) (ported from ColorSettingComponent)
    // Color preview circle with outline ring
    private static void renderColor(DrawContext ctx, HueSetting s, float x, float y, float w,
                                     int mouseX, int mouseY, float anim) {
        // Name (ported: REGULAR 8pt, x+10, centered in 19px - 0.5, alpha=0.75+0.25*hover)
        float nameAlpha = 0.75f * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, s.getDisplayName(),
            x + 10, settingHeaderTextY(y), 7, nameColor);

        // Color preview offsets are configurable in NewGuiState so they can be fine-tuned.
        float previewX = x + w - NewGuiState.COLOR_PREVIEW_OFFSET_RIGHT - NewGuiState.COLOR_PREVIEW_SIZE;
        float previewY = y + NewGuiState.COLOR_PREVIEW_OFFSET_TOP;
        int outlineC = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 145));
        RenderUtil.Round.draw(ctx, previewX, previewY, NewGuiState.COLOR_PREVIEW_SIZE, NewGuiState.COLOR_PREVIEW_SIZE, 4.5f, outlineC);
        RenderUtil.Round.draw(ctx,
            previewX + NewGuiState.COLOR_PREVIEW_INNER_OFFSET,
            previewY + NewGuiState.COLOR_PREVIEW_INNER_OFFSET,
            NewGuiState.COLOR_PREVIEW_INNER_SIZE,
            NewGuiState.COLOR_PREVIEW_INNER_SIZE,
            2f,
            s.getColor());

        if (s.opened) {
            float pickerX = x + 8f;
            float pickerY = y + 20f;
            float pickerW = w - 16f;
            float pickerH = 50f;
            int size = (int) pickerW;
            s.maximum = size;
            float currentGUI = s.current * (size / s.originalMaximum);

            if (s.sliding) {
                currentGUI = (float) ru.strange.client.utils.math.MathHelper.round(
                        ru.strange.client.utils.math.MathHelper.clamp(
                                (float)((mouseX - pickerX) * (s.maximum - s.minimum) / size + s.minimum),
                                s.minimum, s.maximum), s.increment);
                s.current = currentGUI * (s.originalMaximum / s.maximum);
                s.triggerDeferredAutoSave();
            }
            s.sliderWidth = lerp(s.sliderWidth,
                    ((currentGUI - s.minimum) / (s.maximum - s.minimum)) * size, 0.15f);

            float hue = currentGUI / size;

            if (s.colorSliding) {
                s.saturation = ru.strange.client.utils.math.MathHelper.clamp((float)(mouseX - pickerX) / pickerW, 0, 1);
                s.brightness = 1f - ru.strange.client.utils.math.MathHelper.clamp((float)(mouseY - pickerY) / pickerH, 0, 1);
                s.triggerDeferredAutoSave();
            }

            Color fullHue = Color.getHSBColor(hue, 1, 1);
            RenderUtil.Round.draw(ctx, pickerX, pickerY, pickerW, pickerH, 4, fullHue);
            RenderUtil.Image.draw(ctx, COLOR_PICKER_OVERLAY_ICON, pickerX, pickerY, pickerW, pickerH, new Color(255, 255, 255));

            float cX = pickerX + s.saturation * pickerW - 3f;
            float cY = pickerY + (1f - s.brightness) * pickerH - 3f;
            RenderUtil.Border.draw(ctx, cX, cY, 6, 6, 3, 0.5f, new Color(0xFFFFFF));

            float hueBarY = pickerY + pickerH + 4;
            RenderUtil.Image.draw(ctx, HUE_BAR_ICON, pickerX, hueBarY, pickerW, 5, new Color(255, 255, 255));
            RenderUtil.Border.draw(ctx, pickerX - 2.5f + s.sliderWidth, hueBarY - 1, 5, 7, 3, 0.5f, new Color(0xFFFFFF));
        }
    }

    // MultiBooleanSetting: height = 31 + settings*12 (ported from SelectSettingComponent layout)
    // Box with options, checkmark for enabled
    private static void renderMultiBoolean(DrawContext ctx, MultiBooleanSetting s, float x, float y, float w,
                                            float anim) {
        // Name (same layout as ModeSettingComponent)
        float nameAlpha = 0.75f * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, s.getDisplayName(),
            x + 10, settingHeaderTextY(y), 7, nameColor);

        long enabledCount = s.settings.stream().filter(BooleanSetting::get).count();
        if (enabledCount > 0) {
            String counter = enabledCount + "/" + s.settings.size();
            float counterW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, counter, 6);
            int counterColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (anim * 180));
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, counter,
                x + w - counterW - 10f, settingHeaderMetaY(y), 6, counterColor);
        }

        // Box (same as Mode: x+8, y+18, w-16, 8+items*12, radius 6, bgColor alpha 30%)
        float boxX = x + 8f;
        float boxY = y + 18f;
        float boxW = w - 16f;
        float boxH = 8f + s.settings.size() * 12f;
        int boxBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(anim * 76));
        RenderUtil.Round.draw(ctx, boxX, boxY, boxW, boxH, 6, boxBg);

        float offset = 0f;
        for (int i = 0; i < s.settings.size(); i++) {
            BooleanSetting sub = s.settings.get(i);
            boolean enabled = sub.get();

            float optAlpha = (0.75f + (enabled ? 0.25f : 0f)) * anim;
            int optColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(optAlpha * 255));

            float optionTextY = settingOptionTextY(y, offset);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, sub.getDisplayName(),
                x + 16f, optionTextY, 6, optColor);

            if (enabled) {
                float iconAlpha = anim;
                int checkColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(iconAlpha * 255));
                drawCheckIcon(ctx, x + w - 20f, y + 20f + offset + 3f, 6.0f, checkColor);
            }

            offset += 12f;
        }
    }

    // ListSetting: height = 31 + list*12 (ported from SelectSettingComponent layout)
    // Box with options, checkmark for selected
    private static void renderList(DrawContext ctx, ListSetting s, float x, float y, float w,
                                    float anim) {
        // Name
        float nameAlpha = 0.75f * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, s.getDisplayName(),
            x + 10, settingHeaderTextY(y), 7, nameColor);

        String selectedDisplay = s.getSelectedDisplay();
        float selectedW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, selectedDisplay, 6);
        int selectedColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (anim * 180));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, selectedDisplay,
            x + w - selectedW - 10f, settingHeaderMetaY(y), 6, selectedColor);

        // Box
        float boxX = x + 8f;
        float boxY = y + 18f;
        float boxW = w - 16f;
        float boxH = 8f + s.list.size() * 12f;
        int listBoxBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(anim * 76));
        RenderUtil.Round.draw(ctx, boxX, boxY, boxW, boxH, 6, listBoxBg);

        float offset = 0f;
        for (int i = 0; i < s.list.size(); i++) {
            String opt = ModLocalization.raw(s.list.get(i));
            boolean sel = s.isSelected(s.list.get(i));

            float optAlpha = (0.75f + (sel ? 0.25f : 0f)) * anim;
            int optColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(optAlpha * 255));

            float optionTextY = settingOptionTextY(y, offset);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, opt,
                x + 16f, optionTextY, 6, optColor);

            if (sel) {
                float iconAlpha = anim;
                int checkColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(iconAlpha * 255));
                drawCheckIcon(ctx, x + w - 20f, y + 20f + offset + 3f, 6.0f, checkColor);
            }

            offset += 12f;
        }
    }

    // ========== HELPERS ==========

    private static float easeOutCubic(float t) {
        t = Math.max(0, Math.min(1, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float easeOutQuart(float t) {
        t = Math.max(0, Math.min(1, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv * inv;
    }

    private static float easeOutBack(float t) {
        t = Math.max(0, Math.min(1, t));
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    private static float easeInOutCubic(float t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5f
            ? 4f * t * t * t
            : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    private static float easeInCubic(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * t;
    }

    private static float centeredTextY(float y, float height, FontDraw.FontType fontType, int fontSize) {
        float fontHeight = FontDraw.getHeight(fontType, fontSize);
        float ascent = FontDraw.getAscent(fontType, fontSize);
        return y + (height - fontHeight) / 2f + ascent;
    }

    private static float centeredContentY(float y, float height, float contentSize) {
        return y + (height - contentSize) / 2f;
    }

    private static void drawCheckIcon(DrawContext ctx, float x, float y, float size, int color) {
        RenderUtil.Image.draw(ctx, CHECK_ICON, x, y, size, size, color);
    }

    private static float settingHeaderTextY(float y) {
        return centeredTextY(y, 19f, FontDraw.FontType.MEDIUM, 7) - 0.5f;
    }

    private static float settingHeaderMetaY(float y) {
        return centeredTextY(y, 19f, FontDraw.FontType.MEDIUM, 6) - 0.5f;
    }

    private static float settingOptionTextY(float y, float offset) {
        return centeredTextY(y + 20f + offset, 12f, FontDraw.FontType.MEDIUM, 6) - 0.5f;
    }

    // Additional color (ported from Rockstar theme: dark ≈ (24,24,27), light ≈ (200,200,206))
    private static int additionalColor(boolean dark, int alpha) {
        int c = dark ? DARK_ADDITIONAL_RGB : LIGHT_ADDITIONAL_RGB;
        return RenderUtil.ColorUtil.replAlpha(c, Math.max(0, Math.min(255, alpha)));
    }

    private static float lerp(float from, float to, float speed) {
        return NewGuiState.smoothLerp(from, to, speed);
    }

    private static String trimSliderValue(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return Integer.toString(Math.round(value));
        }
        String s = String.format(java.util.Locale.ROOT, "%.2f", value);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    private static int mixColor(int from, int to, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int fr = (from >> 16) & 255, fg = (from >> 8) & 255, fb = from & 255;
        int tr = (to >> 16) & 255, tg = (to >> 8) & 255, tb = to & 255;
        int r = (int) (fr + (tr - fr) * clamped);
        int g = (int) (fg + (tg - fg) * clamped);
        int b = (int) (fb + (tb - fb) * clamped);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int themeAccentColor(int alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), Math.max(0, Math.min(255, alpha)));
    }

    private static Identifier categoryIcon(Category category) {
        return switch (category) {
            case Player -> PLAYER_ICON;
            case World -> WORLD_ICON;
            case Utilities -> UTILITIES_ICON;
            case Other -> OTHER_ICON;
            case Interface -> INTERFACE_ICON;
            case Theme -> THEME_ICON;
            case Combat -> OTHER_ICON;
        };
    }

    private static void drawSurfaceCard(DrawContext ctx, float x, float y, float width, float height,
                                        float radius, float alpha, boolean dark, float tone) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        Theme theme = ThemeManager.getTheme();
        boolean transparent = theme == Theme.TRANSPARENT_BLACK || theme == Theme.TRANSPARENT_WHITE;
        // Alpha from theme: BLACK=0.9, WHITE=0.7, TRANSPARENT=0.6, PINK/PURPLE=0.7
        float baseAlpha;
        if (transparent) {
            baseAlpha = 0.6f;
        } else if (theme == Theme.PINK || theme == Theme.PURPLE) {
            baseAlpha = 0.7f;
        } else {
            baseAlpha = dark ? 0.9f : 0.7f;
        }

        ClickGui clickGui = ClickGui.getInstance();
        if (clickGui != null && clickGui.isGlassEnabled()) {
            float glassAlpha = Math.max(0.0f, Math.min(1f, clickGui.getGlassAlpha() * tone * clamped));
            int tint = 0xFFEAF2FA;
            RenderUtil.LiquidGlass.draw(ctx, x, y, width, height, radius, tint,
                    clickGui.getGlassBlur(), glassAlpha);
            return;
        }

        int bg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1),
            (int) (255f * baseAlpha * tone * clamped));
        RenderUtil.Round.draw(ctx, x, y, width, height, radius, bg);
    }

    private static String text(String ru, String en) {
        return GuiLocalization.currentLanguage() == GuiLanguage.EN ? en : ru;
    }
}
