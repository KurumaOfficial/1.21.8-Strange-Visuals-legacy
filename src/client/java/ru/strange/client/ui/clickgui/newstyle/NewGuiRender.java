package ru.strange.client.ui.clickgui.newstyle;

// BoxingHarmoni: Strenge Visual GUI implementation

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.manager.promo.PromoCodeManager;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.*;
import ru.strange.client.module.impl.interfaces.ClickGui;
import ru.strange.client.ui.clickgui.localization.GuiLanguage;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;
import ru.strange.client.utils.math.ScrollUtil;
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

        if (NewGuiState.closing) {
            int fadeAlpha = (int) ((1f - anim) * 140f);
            if (fadeAlpha > 0) {
                ctx.fill(0, 0, screenWidth, screenHeight, fadeAlpha << 24);
            }
        }

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

            float px = NewGuiState.getRenderedPanelX(i);
            float py = NewGuiState.panelY[i] + NewGuiState.getPanelSlideOffset(i);

            // Scale: 0.85→1.0 on open, 1.0→0.88 on close
            float scale = NewGuiState.getPanelRenderScale(i);
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

        NewGuiState.themeDropdownAnimation = lerp(
                NewGuiState.themeDropdownAnimation,
                NewGuiState.themeDropdownOpened ? 1f : 0f,
                0.18f
        );

        // Theme bar + search field adaptation for current base
        renderThemeBar(ctx, mouseX, mouseY, anim, screenWidth, light);
        renderSearch(ctx, anim, dark, searchCursorVisible);
        renderLanguageSwitch(ctx, mouseX, mouseY, anim, dark);

        // -- BoxingHarmoni --
        renderPromoSection(ctx, mouseX, mouseY, anim, dark, textCursorVisible);
        renderBranding(ctx, anim, screenWidth, screenHeight);
    }

    private static void renderBranding(DrawContext ctx, float anim, int screenWidth, int screenHeight) {
        if (anim < 0.1f) return;
        int color1 = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 180));
        int color2 = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 110));

        float y = screenHeight - 25f;
        FontDraw.drawCenter(FontDraw.FontType.SEMIBOLD, ctx, "Strenge Visual", screenWidth / 2f, y, 9, color1, false);
        FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, "BoxingHarmoni", screenWidth / 2f, y + 10f, 7, color2, false);
    }

    private static void renderPromoSection(DrawContext ctx, int mouseX, int mouseY, float anim, boolean dark, boolean cursorVisible) {
        if (anim < 0.1f) return;

        float w = 120f;
        float h = 18f;
        float x = 10f; // Bottom left
        float y = NewGuiState.currentScreenHeight - 30f;

        // Background
        drawSurfaceCard(ctx, x, y, w, h, 6f, anim * 0.8f, dark, 0.8f);

        // Input text
        String display = NewGuiState.promoInputText;
        if (display.isEmpty() && !NewGuiState.promoInputFocused) {
            display = text("Промокод", "Promo Code");
        }
        if (NewGuiState.promoInputFocused && cursorVisible) {
            display += "|";
        }

        int textColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 200));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, display, x + 6, centeredTextY(y, h, FontDraw.FontType.MEDIUM, 7) - 0.5f, 7, textColor);

        // Apply Button
        float btnW = 35f;
        float btnX = x + w + 4f;
        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= y && mouseY <= y + h;
        float btnAlpha = (hovered ? 1.0f : 0.8f) * anim;

        drawSurfaceCard(ctx, btnX, y, btnW, h, 6f, btnAlpha, dark, 0.9f);
        int btnTextColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(btnAlpha * 255));
        FontDraw.drawCenter(FontDraw.FontType.SEMIBOLD, ctx, "OK", btnX + btnW / 2f, centeredTextY(y, h, FontDraw.FontType.SEMIBOLD, 7) - 0.5f, 7, btnTextColor, false);

        // Message
        if (!NewGuiState.promoMessage.isEmpty()) {
            NewGuiState.promoMessageAlpha = lerp(NewGuiState.promoMessageAlpha, 1f, 0.05f);
            int msgColor = NewGuiState.promoMessageAccepted ? 0xFF90EE90 : 0xFFFFB6B6;
            msgColor = RenderUtil.ColorUtil.replAlpha(msgColor, (int)(NewGuiState.promoMessageAlpha * anim * 255));
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, NewGuiState.promoMessage, x, y - 10f, 6, msgColor);

            if (NewGuiState.promoMessageAlpha > 0.9f && !cursorVisible) { // Blink out message eventually? No, just keep it.
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
                 FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, text("Ctrl+←/→ - смена GUI", "Ctrl+←/→ - switch GUI"),
                     NewGuiState.currentScreenWidth / 2f, hintBaseY + 20f, 7, hintColor, false);
                 FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, text("Alt+ЛКМ - перемещение HUD", "Alt+LMB - move HUD"),
                     NewGuiState.currentScreenWidth / 2f, hintBaseY + 30f, 7, hintColor, false);
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
             FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, text("Ctrl+←/→ - смена GUI", "Ctrl+←/→ - switch GUI"),
                 NewGuiState.currentScreenWidth / 2f, tooltipY + 10f, 7, tooltipColor, false);
             FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, text("Alt+ЛКМ - перемещение HUD", "Alt+LMB - move HUD"),
                 NewGuiState.currentScreenWidth / 2f, tooltipY + 20f, 7, tooltipColor, false);
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

        String value = s.percent ? Math.round(s.get() * 100f) + "%" : trimSliderValue(s.get());
        float valW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, value, 6);
        float maxNameWidth = Math.max(24f, sliderW - valW - 14f);
        String sliderName = truncateText(s.getDisplayName(), FontDraw.FontType.MEDIUM, 7, maxNameWidth);

        float nameAlpha = 0.75f * anim;
        int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(nameAlpha * 255));
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, sliderName,
            x + 10, centeredTextY(y + 2f, 11f, FontDraw.FontType.MEDIUM, 7), 7, nameColor);

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

    private static String truncateText(String text, FontDraw.FontType fontType, int fontSize, float maxWidth) {
        if (text == null || text.isEmpty()) return text;
        if (FontDraw.getWidth(fontType, text, fontSize) <= maxWidth) return text;

        String ellipsis = "...";
        float ellipsisWidth = FontDraw.getWidth(fontType, ellipsis, fontSize);

        for (int i = text.length() - 1; i > 0; i--) {
            String truncated = text.substring(0, i) + ellipsis;
            if (FontDraw.getWidth(fontType, truncated, fontSize) <= maxWidth) {
                return truncated;
            }
        }

        return ellipsis;
    }

    private static void renderPanel(DrawContext ctx, int panelIndex, int mouseX, int mouseY, float sizing,
                                    float px, float anim, boolean dark, boolean light, float shakeWave, boolean textCursorVisible) {
        Category category = NewGuiState.CATEGORIES[panelIndex];
        float panelX = NewGuiState.panelX[panelIndex];
        float panelY = NewGuiState.panelY[panelIndex];

        // Panel background
        drawSurfaceCard(ctx, panelX, panelY, NewGuiState.PANEL_WIDTH, NewGuiState.PANEL_HEIGHT, 6f, anim * 0.9f, dark, 0.85f);

        // Category header
        int headerColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 220));
        String categoryName = category.getName();
        // Truncate category name if too long
        float maxHeaderWidth = NewGuiState.PANEL_WIDTH - 24f;
        float headerWidth = FontDraw.getWidth(FontDraw.FontType.SEMIBOLD, categoryName, 8);
        if (headerWidth > maxHeaderWidth) {
            categoryName = truncateText(categoryName, FontDraw.FontType.SEMIBOLD, 8, maxHeaderWidth);
        }
        FontDraw.drawCenter(FontDraw.FontType.SEMIBOLD, ctx, categoryName, panelX + NewGuiState.PANEL_WIDTH / 2f, panelY + 14f, 8, headerColor, false);

        // Category icon
        Identifier icon = categoryIcon(category);
        RenderUtil.Image.draw(ctx, icon, panelX + 8f, panelY + 6f, 12f, 12f, RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 180)));

        Module selected = NewGuiState.selectedModule[panelIndex];
        if (selected != null) {
            renderSettingsView(ctx, selected, panelIndex, panelX, panelY, mouseX, mouseY, anim, dark, light, textCursorVisible);
            return;
        }

        // Render modules

        List<Module> modules = NewGuiState.getVisibleModules(category);
        ScrollUtil scroll = NewGuiState.modulesScroll[panelIndex];
        float listY = panelY + NewGuiState.HEADER_HEIGHT + NewGuiState.SEPARATOR_HEIGHT;
        float listH = NewGuiState.PANEL_HEIGHT - NewGuiState.HEADER_HEIGHT - NewGuiState.SEPARATOR_HEIGHT;
        float contentHeight = Math.max(0f, modules.size() * (NewGuiState.MODULE_HEIGHT + 2f) - 2f);
        scroll.setMax(contentHeight, listH);
        scroll.update();
        float scrollY = scroll.getScroll();
        float moduleY = listY + scrollY;

        ctx.enableScissor(
                (int) panelX,
                (int) listY,
                (int) (panelX + NewGuiState.PANEL_WIDTH),
                (int) (listY + listH)
        );

        for (Module module : modules) {
            if (moduleY > listY + listH) break;

            float moduleHeight = NewGuiState.MODULE_HEIGHT;
            if (moduleY + moduleHeight < listY) {
                moduleY += moduleHeight + 2f;
                continue;
            }
            double[] panelMouse = NewGuiState.toPanelMouse(panelIndex, mouseX, mouseY);
            boolean hovered = !Double.isNaN(panelMouse[0])
                    && panelMouse[0] >= panelX + 4f && panelMouse[0] <= panelX + NewGuiState.PANEL_WIDTH - 4f
                    && panelMouse[1] >= moduleY && panelMouse[1] <= moduleY + moduleHeight;
            float hoverAnim = lerp(NewGuiState.getHover(module), hovered ? 1f : 0f, 0.14f);
            float enableAnim = lerp(NewGuiState.getEnable(module), module.enable ? 1f : 0f, 0.12f);
            NewGuiState.setHover(module, hoverAnim);
            NewGuiState.setEnable(module, enableAnim);
            if (hovered) {
                NewGuiState.hoveredDescription = module.getLocalizedDescription();
            }

            // Module background
            float moduleAlpha = (0.6f + 0.4f * hoverAnim) * anim;
            int moduleBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(moduleAlpha * 180));
            RenderUtil.Round.draw(ctx, panelX + 4f, moduleY, NewGuiState.PANEL_WIDTH - 8f, moduleHeight, 4f, moduleBg);

            // Module name
            int nameColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * (220 + 35 * enableAnim)));
            String moduleName = module.binding ? module.getDisplayName() : module.getLocalizedName();
            if (!module.binding) {
                String bindText = KeyUtil.getKey(module.bind);
                if (!bindText.equals("null")) {
                    moduleName += " [" + bindText + "]";
                }
            }
            // Truncate module name if too long
            float maxNameWidth = NewGuiState.PANEL_WIDTH - 30f;
            float nameWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, moduleName, 7);
            if (nameWidth > maxNameWidth) {
                moduleName = truncateText(moduleName, FontDraw.FontType.MEDIUM, 7, maxNameWidth);
            }
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, moduleName, panelX + 12f,
                    centeredTextY(moduleY, moduleHeight, FontDraw.FontType.MEDIUM, 7) - 0.5f,
                    7, nameColor);

            // Enable indicator
            if (enableAnim > 0.01f) {
                int indicatorColor = themeAccentColor((int)(enableAnim * anim * 255));
                RenderUtil.Round.draw(ctx, panelX + NewGuiState.PANEL_WIDTH - 14f, moduleY + 6f, 6f, 6f, 3f, indicatorColor);
            }

            moduleY += moduleHeight + 2f;
        }
        ctx.disableScissor();
    }

    private static void renderSettingsView(DrawContext ctx, Module module, int panelIndex, float panelX, float panelY,
                                           int mouseX, int mouseY, float anim, boolean dark, boolean light,
                                           boolean textCursorVisible) {
        float backY = panelY + NewGuiState.HEADER_HEIGHT + NewGuiState.SEPARATOR_HEIGHT;
        int textColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (anim * 220));
        RenderUtil.Image.draw(ctx, BACK_ARROW_ICON,
                panelX + NewGuiState.BACK_ARROW_OFFSET_LEFT,
                backY + 10f + NewGuiState.BACK_ARROW_OFFSET_TOP,
                NewGuiState.BACK_ARROW_SIZE,
                NewGuiState.BACK_ARROW_SIZE,
                textColor);

        String title = truncateText(module.getLocalizedName(), FontDraw.FontType.MEDIUM, 7,
                NewGuiState.PANEL_WIDTH - 28f);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, title,
                panelX + NewGuiState.BACK_ARROW_OFFSET_LEFT + NewGuiState.BACK_ARROW_SIZE + NewGuiState.BACK_TITLE_GAP,
                backY + 17f,
                7,
                textColor);

        float listY = backY + NewGuiState.HEADER_HEIGHT;
        float listH = NewGuiState.PANEL_HEIGHT - NewGuiState.HEADER_HEIGHT * 2f - NewGuiState.SEPARATOR_HEIGHT;
        ScrollUtil scroll = NewGuiState.settingsScroll[panelIndex];
        float contentHeight = getSettingsContentHeight(module);
        scroll.setMax(contentHeight, listH);
        scroll.update();
        float scrollY = scroll.getScroll();
        float offset = 0f;

        ctx.enableScissor(
                (int) panelX,
                (int) listY,
                (int) (panelX + NewGuiState.PANEL_WIDTH),
                (int) (listY + listH)
        );

        for (Setting setting : module.getSettingsForGUI()) {
            float settingHeight = NewGuiState.measureSettingHeight(setting);
            if (settingHeight <= 0f) {
                continue;
            }

            float settingY = listY + offset + scrollY;
            if (settingY + settingHeight >= listY && settingY <= listY + listH) {
                renderSetting(ctx, setting, panelX, settingY, NewGuiState.PANEL_WIDTH,
                        mouseX, mouseY, anim, dark, light, textCursorVisible);
            }
            offset += settingHeight;
        }

        ctx.disableScissor();

    }

    private static float getSettingsContentHeight(Module module) {
        float height = 0f;
        for (Setting setting : module.getSettingsForGUI()) {
            height += NewGuiState.measureSettingHeight(setting);
        }
        return height;
    }

    private static void renderDescription(DrawContext ctx, float anim, int screenWidth, int screenHeight) {
        if (NewGuiState.hoveredDescription.isEmpty()) return;

        float descAlpha = lerp(NewGuiState.descriptionAlpha, !NewGuiState.hoveredDescription.isEmpty() ? 1f : 0f, 0.18f);
        NewGuiState.descriptionAlpha = descAlpha;

        if (descAlpha < 0.01f) return;

        String desc = NewGuiState.hoveredDescription;
        float maxTextWidth = Math.min(300f, screenWidth - 40f);
        List<String> lines = wrapDescription(desc, maxTextWidth, 7);
        float lineHeight = 10f;
        float paddingX = 12f;
        float paddingY = 8f;
        float textBlockWidth = 0f;
        for (String line : lines) {
            textBlockWidth = Math.max(textBlockWidth, FontDraw.getWidth(FontDraw.FontType.MEDIUM, line, 7));
        }
        float descWidth = textBlockWidth + paddingX * 2f;
        float descHeight = paddingY * 2f + lines.size() * lineHeight;
        float descX = (screenWidth - descWidth) / 2f;
        float descY = screenHeight - descHeight - 72f + NewGuiState.descriptionOffsetY * (1f - descAlpha);

        int descBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(descAlpha * anim * 235));
        RenderUtil.Round.draw(ctx, descX, descY, descWidth, descHeight, 6f, descBg);
        RenderUtil.Border.draw(ctx, descX, descY, descWidth, descHeight, 6f, 0.35f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), (int)(descAlpha * anim * 90)));

        int descColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(descAlpha * anim * 255));
        float textY = descY + paddingY + 6f;
        for (String line : lines) {
            FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, line, descX + descWidth / 2f, textY, 7, descColor, false);
            textY += lineHeight;
        }
    }

    private static List<String> wrapDescription(String text, float maxWidth, int fontSize) {
        List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (FontDraw.getWidth(FontDraw.FontType.MEDIUM, candidate, fontSize) <= maxWidth) {
                current = new StringBuilder(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
                current = new StringBuilder(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add(text);
        }
        return lines;
    }

    private static void renderThemeBar(DrawContext ctx, int mouseX, int mouseY, float anim, int screenWidth, boolean light) {
        Theme[] themes = Theme.values();
        float boxWidth = 100f;
        float boxHeight = 20f;
        float boxX = screenWidth - boxWidth - 10f;
        float boxY = NewGuiState.getThemeBarY();

        Theme currentTheme = ThemeManager.getTheme();
        boolean hovered = mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= boxY && mouseY <= boxY + boxHeight;

        // Фон кнопки
        float bgAlpha = (0.6f + 0.4f * (hovered ? 1f : 0f)) * anim;
        int themeBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(bgAlpha * 180));
        RenderUtil.Round.draw(ctx, boxX, boxY, boxWidth, boxHeight, 4f, themeBg);

        // Текст текущей темы
        int textColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(anim * 220));
        String themeName = currentTheme.getName();
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, themeName, boxX + 8f, boxY + 6f, 7, textColor);

        // Иконка "стрелочка"
        float arrowX = boxX + boxWidth - 16f;
        float arrowY = boxY + 8f;
        RenderUtil.Rect.draw(ctx, arrowX, arrowY, 6f, 1.5f, textColor);
        RenderUtil.Rect.draw(ctx, arrowX + 1.5f, arrowY + 1.5f, 3f, 1.5f, textColor);

        // Рендер шторки со скроллом
        if (NewGuiState.themeDropdownAnimation > 0.01f) {
            float optionHeight = 20f;
            int maxVisible = 5; // Лимит видимых тем
            int visibleCount = Math.min(themes.length, maxVisible);
            float targetDropdownHeight = visibleCount * optionHeight + 4f;

            float animatedDropdownHeight = targetDropdownHeight * easeOutQuart(NewGuiState.themeDropdownAnimation);
            float dropdownY = boxY - animatedDropdownHeight + 4f;

            int dropdownBg = RenderUtil.ColorUtil.replAlpha(
                    RenderUtil.ColorUtil.getBackGroundColor(1, 1),
                    (int)(anim * 200)
            );
            RenderUtil.Round.draw(ctx, boxX, dropdownY, boxWidth, animatedDropdownHeight, 4f, dropdownBg);

            // Настройка лимитов скролла
            float totalContentHeight = themes.length * optionHeight;
            float maxScroll = Math.max(0, totalContentHeight - (targetDropdownHeight - 4f));
            NewGuiState.themeScroll.setMax(maxScroll, targetDropdownHeight - 4f);
            float scrollOffset = NewGuiState.themeScroll.getScroll();

            // Визуальные границы для обрезки
            float clipStartY = dropdownY + 2f;
            float clipEndY = dropdownY + animatedDropdownHeight - 2f;

            for (int i = 0; i < themes.length; i++) {
                Theme theme = themes[i];
                float optionY = dropdownY + 2f + i * optionHeight + scrollOffset;

                // Пропускаем рендер, если элемент за пределами списка
                if (optionY + optionHeight < clipStartY || optionY > clipEndY) {
                    continue;
                }

                boolean optionHovered =
                        mouseX >= boxX && mouseX <= boxX + boxWidth &&
                                mouseY >= optionY && mouseY <= optionY + optionHeight &&
                                mouseY >= clipStartY && mouseY <= clipEndY;

                if (optionHovered) {
                    int optionBg = RenderUtil.ColorUtil.replAlpha(
                            RenderUtil.ColorUtil.getTextColor(1, 1),
                            (int)(0.3f * anim * 100)
                    );
                    // Рендерим подсветку с обрезкой
                    float drawY = Math.max(optionY, clipStartY);
                    float drawH = Math.min(optionHeight, clipEndY - drawY);
                    RenderUtil.Round.draw(ctx, boxX + 2f, drawY, boxWidth - 4f, drawH, 3f, optionBg);
                }

                if (theme == currentTheme) {
                    int selectColor = themeAccentColor((int)(anim * 255));
                    float circleY = optionY + 7f;
                    if (circleY >= clipStartY && circleY + 4f <= clipEndY) {
                        RenderUtil.Round.draw(ctx, boxX + boxWidth - 10f, circleY, 4f, 4f, 2f, selectColor);
                    }
                }

                // Простая проверка видимости для текста
                float textY = optionY + 6f;
                if (textY >= clipStartY - 2f && textY + 7f <= clipEndY + 2f) {
                    int optionTextColor = RenderUtil.ColorUtil.replAlpha(
                            RenderUtil.ColorUtil.getTextColor(1, 1),
                            (int)(anim * 200)
                    );
                    FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, theme.getName(), boxX + 8f, textY, 7, optionTextColor);
                }
            }
        }
    }
}
