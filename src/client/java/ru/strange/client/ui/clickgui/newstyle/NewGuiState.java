package ru.strange.client.ui.clickgui.newstyle;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.*;
import ru.strange.client.utils.math.ScrollUtil;
import ru.strange.client.utils.other.ModuleVisibilityUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NewGuiState {
    public static MinecraftClient mc = MinecraftClient.getInstance();

    public static final Category[] CATEGORIES = {
            Category.Player, Category.World, Category.Utilities, Category.Other, Category.Interface
    };

    public static final float PANEL_WIDTH = 115f;
    public static final float PANEL_HEIGHT = 240f;
    public static final float PANEL_SPACING = 10f;
    public static final float HEADER_HEIGHT = 24f;
    public static final float MODULE_HEIGHT = 20f;
    public static final float SEPARATOR_HEIGHT = 4f;
    public static final float SEARCH_WIDTH = 100f;
    public static final float SEARCH_HEIGHT = 20f;
    public static final float SEARCH_MARGIN_BOTTOM = 20f;

    public static final float BACK_ARROW_SIZE = 6f;
     public static final float BACK_ARROW_OFFSET_LEFT = 6f;
    public static final float BACK_ARROW_OFFSET_TOP = 2f;
    public static final float BACK_TITLE_GAP = 6f;
    public static final float BACK_HITBOX_PADDING_LEFT = 2f;
    public static final float BACK_HITBOX_PADDING_RIGHT = 6f;

    public static final float COLOR_PREVIEW_SIZE = 8f;
    public static final float COLOR_PREVIEW_INNER_SIZE = 6f;
    public static final float COLOR_PREVIEW_OFFSET_RIGHT = 10f;
    public static final float COLOR_PREVIEW_OFFSET_TOP = 7f;
    public static final float COLOR_PREVIEW_INNER_OFFSET = 1f;
    public static final float COLOR_PREVIEW_CLICK_PADDING = 2f;

    public static final float LANGUAGE_SWITCH_WIDTH = 52f;
    public static final float LANGUAGE_SWITCH_HEIGHT = 16f;
    public static final float LANGUAGE_SWITCH_OFFSET_RIGHT = 0f;
    public static final float LANGUAGE_SWITCH_OFFSET_TOP = -22f;
    public static final float LANGUAGE_SWITCH_PADDING = 2f;
    public static final float LANGUAGE_SWITCH_SEGMENT_GAP = 2f;
    public static final float LANGUAGE_SWITCH_PILL_Y_OFFSET = 1f;
    public static final float LANGUAGE_SWITCH_PILL_HEIGHT_REDUCTION = 2f;
    public static final float LANGUAGE_SWITCH_TEXT_Y_OFFSET = 0.5f;

    public static float openAnimation = 0f;
    public static boolean closing = false;

    /** Nanos of the previous frame, used for framerate-independent animation. */
    public static long lastFrameNanos = 0L;
    /** Seconds elapsed since the previous frame, clamped to [0.0001, 0.066]. */
    public static float deltaSeconds = 0f;
    private static final float MIN_DT = 1.0e-4f;
    private static final float MAX_DT = 1.0f / 15.0f;
    private static final float BASE_FPS = 165.0f;

    public static final float[] panelX = new float[5];
    public static final float[] panelY = new float[5];

    public static final ScrollUtil[] modulesScroll = new ScrollUtil[5];
    public static final ScrollUtil[] settingsScroll = new ScrollUtil[5];

    public static final Module[] selectedModule = new Module[5];
    public static final Module[] lastSelectedModule = new Module[5];

    public static final float[] swapAnimation = new float[5];

    public static final Map<Module, Float> hoverAnimations = new HashMap<>();
    public static final Map<Module, Float> enableAnimations = new HashMap<>();
    public static final Map<Module, Float> blockingAnimations = new HashMap<>();
    public static final Map<Module, Float> shakeAnimations = new HashMap<>();

    public static final Map<Setting, Float> settingToggleAnimations = new HashMap<>();

    public static final Map<Theme, Float> themeHoverAnimations = new HashMap<>();
    public static final Map<Theme, Float> themeSelectAnimations = new HashMap<>();

    public static final float[] panelSizing = new float[5];

    public static String hoveredDescription = "";
    public static float descriptionAlpha = 0f;
    public static float descriptionOffsetY = -8f; 
    
    public static String searchQuery = "";
    public static boolean searchFocused = false;
    public static float searchAnimation = 0f;
    public static float searchAppendAnimation = 0f;
    public static float languageHoverRu = 0f;
    public static float languageHoverEn = 0f;
    public static float languageSelectionAnimation = 0f;
    public static boolean languageSelectionInitialized = false;

    private static final Map<Category, List<Module>> visibleModulesCache = new HashMap<>();
    private static String lastCachedSearchQuery = "";
    private static long lastModuleCacheTime = 0L;
    private static final long MODULE_CACHE_INTERVAL_MS = 200L;

    /**
     * Call at the start of each render frame to update deltaSeconds.
     */
    public static void updateFrameDelta() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            deltaSeconds = 1.0f / BASE_FPS;
            return;
        }
        long elapsed = now - lastFrameNanos;
        lastFrameNanos = now;
        if (elapsed < 0L) elapsed = 0L;
        float dt = elapsed / 1_000_000_000.0f;
        if (dt < MIN_DT) dt = MIN_DT;
        else if (dt > MAX_DT) dt = MAX_DT;
        deltaSeconds = dt;
    }

    /**
     * Framerate-independent exponential lerp.
     * speed is calibrated for 60 FPS — at 144 FPS the step is smaller per frame,
     * at 30 FPS the step is larger, keeping the perceived speed identical.
     */
    public static float smoothLerp(float from, float to, float speed) {
        float exponent = deltaSeconds * BASE_FPS;
        float factor = 1.0f - (float) Math.pow(1.0f - speed, exponent);
        return from + (to - from) * factor;
    }

    public static int currentScreenWidth;
    public static int currentScreenHeight;
    public static SliderSetting currentSliderSetting;

    public static boolean positionsInitialized = false;

    static {
        for (int i = 0; i < CATEGORIES.length; i++) {
            modulesScroll[i] = new ScrollUtil();
            settingsScroll[i] = new ScrollUtil();
        }
    }

    public static void initPositions(int screenWidth, int screenHeight) {
        if (positionsInitialized && currentScreenWidth == screenWidth && currentScreenHeight == screenHeight) {
            return;
        }
        currentScreenWidth = screenWidth;
        currentScreenHeight = screenHeight;
        // Center all panels horizontally with spacing (like DropDownScreen.render)
        float totalWidth = CATEGORIES.length * PANEL_WIDTH + (CATEGORIES.length - 1) * PANEL_SPACING;
        float startX = (screenWidth - totalWidth) / 2f;
        float startY = (screenHeight - PANEL_HEIGHT) / 2f;
        for (int i = 0; i < CATEGORIES.length; i++) {
            panelX[i] = startX + i * (PANEL_WIDTH + PANEL_SPACING);
            panelY[i] = startY;
        }
        positionsInitialized = true;
    }

    public static int getCategoryIndex(Category cat) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i] == cat) return i;
        }
        return -1;
    }

    public static List<Module> getVisibleModules(Category category) {
        long now = System.currentTimeMillis();
        boolean searchChanged = !searchQuery.equals(lastCachedSearchQuery);
        boolean expired = now - lastModuleCacheTime > MODULE_CACHE_INTERVAL_MS;
        if (searchChanged || expired || !visibleModulesCache.containsKey(category)) {
            List<Module> result = new ArrayList<>();
            for (Module m : Strange.get.manager.getType(category)) {
                if (ModuleVisibilityUtil.shouldShow(m) && matchesSearch(m)) {
                    result.add(m);
                }
            }
            result.sort(Comparator.comparing(NewGuiState::moduleSortKey, String.CASE_INSENSITIVE_ORDER));
            visibleModulesCache.put(category, result);
            if (searchChanged) {
                lastCachedSearchQuery = searchQuery;
            }
            lastModuleCacheTime = now;
            return result;
        }
        return visibleModulesCache.get(category);
    }

    public static boolean matchesSearch(Module module) {
        String query = normalizeSearch(searchQuery);
        if (query.isEmpty()) {
            return true;
        }
        return containsQuery(module.name, query)
                || containsQuery(module.getLocalizedName(), query)
                || containsQuery(module.description, query)
                || containsQuery(module.getLocalizedDescription(), query);
    }

    public static float getSearchX() {
        return currentScreenWidth / 2f - SEARCH_WIDTH / 2f;
    }

    public static float getSearchY() {
        return currentScreenHeight - SEARCH_MARGIN_BOTTOM - SEARCH_HEIGHT * Math.max(0.0f, searchAnimation);
    }

    public static float getThemeBarY() {
        return getSearchY() - 24f;
    }

    public static float getLanguageSwitchX() {
        return panelX[CATEGORIES.length - 1] + PANEL_WIDTH - LANGUAGE_SWITCH_WIDTH - LANGUAGE_SWITCH_OFFSET_RIGHT;
    }

    public static float getLanguageSwitchY() {
        return panelY[0] + LANGUAGE_SWITCH_OFFSET_TOP;
    }

    public static float getLanguageInnerX() {
        return getLanguageSwitchX() + LANGUAGE_SWITCH_PADDING;
    }

    public static float getLanguageInnerY() {
        return getLanguageSwitchY() + LANGUAGE_SWITCH_PADDING;
    }

    public static float getLanguageInnerWidth() {
        return LANGUAGE_SWITCH_WIDTH - LANGUAGE_SWITCH_PADDING * 2f;
    }

    public static float getLanguageInnerHeight() {
        return LANGUAGE_SWITCH_HEIGHT - LANGUAGE_SWITCH_PADDING * 2f;
    }

    public static float getLanguageSegmentWidth() {
        return (getLanguageInnerWidth() - LANGUAGE_SWITCH_SEGMENT_GAP) / 2f;
    }

    public static float getLanguageRuX() {
        return getLanguageInnerX();
    }

    public static float getLanguageEnX() {
        return getLanguageInnerX() + getLanguageSegmentWidth() + LANGUAGE_SWITCH_SEGMENT_GAP;
    }

    public static float getLanguagePillY() {
        return getLanguageInnerY() + LANGUAGE_SWITCH_PILL_Y_OFFSET;
    }

    public static float getLanguagePillHeight() {
        return getLanguageInnerHeight() - LANGUAGE_SWITCH_PILL_HEIGHT_REDUCTION;
    }

    public static float getRenderedPanelX(int index) {
        float centerX = currentScreenWidth / 2f - PANEL_WIDTH / 2f;
        float closingFactor = closing ? 1f - openAnimation : 0f;
        return panelX[index] + (centerX - panelX[index]) * closingFactor;
    }

    public static float getHover(Module m) {
        return hoverAnimations.getOrDefault(m, 0f);
    }

    public static void setHover(Module m, float v) {
        hoverAnimations.put(m, v);
    }

    public static float getEnable(Module m) {
        return enableAnimations.getOrDefault(m, m.enable ? 1f : 0f);
    }

    public static void setEnable(Module m, float v) {
        enableAnimations.put(m, v);
    }

    public static float getSettingToggle(Setting s) {
        if (s instanceof BooleanSetting b) return settingToggleAnimations.getOrDefault(s, b.get() ? 1f : 0f);
        return settingToggleAnimations.getOrDefault(s, 0f);
    }

    public static void setSettingToggle(Setting s, float v) {
        settingToggleAnimations.put(s, v);
    }

    public static void resetState() {
        for (int i = 0; i < CATEGORIES.length; i++) {
            selectedModule[i] = null;
            lastSelectedModule[i] = null;
            swapAnimation[i] = 0f;
            panelSizing[i] = 0f;
            modulesScroll[i].reset();
            settingsScroll[i].reset();
        }
        hoverAnimations.clear();
        enableAnimations.clear();
        blockingAnimations.clear();
        shakeAnimations.clear();
        settingToggleAnimations.clear();
        themeHoverAnimations.clear();
        themeSelectAnimations.clear();
        hoveredDescription = "";
        descriptionAlpha = 0f;
        descriptionOffsetY = -8f;
        searchQuery = "";
        searchFocused = false;
        searchAnimation = 0f;
        searchAppendAnimation = 0f;
        languageHoverRu = 0f;
        languageHoverEn = 0f;
        languageSelectionAnimation = 0f;
        languageSelectionInitialized = false;
        currentScreenWidth = 0;
        currentScreenHeight = 0;
        currentSliderSetting = null;
        positionsInitialized = false;
        closing = false;
        lastFrameNanos = 0L;
        deltaSeconds = 0f;
        visibleModulesCache.clear();
        lastCachedSearchQuery = "";
        lastModuleCacheTime = 0L;
    }

    public static float getThemeHover(Theme t) {
        return themeHoverAnimations.getOrDefault(t, 0f);
    }

    public static void setThemeHover(Theme t, float v) {
        themeHoverAnimations.put(t, v);
    }

    public static float getThemeSelect(Theme t) {
        return themeSelectAnimations.getOrDefault(t, ThemeManager.getTheme() == t ? 1f : 0f);
    }

    public static void setThemeSelect(Theme t, float v) {
        themeSelectAnimations.put(t, v);
    }

    public static void resetInteractionState() {
        if (Strange.get == null || Strange.get.manager == null) return;
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                m.binding = false;
                m.displayName = m.name;
                for (Setting s : m.getSettingsForGUI()) {
                    if (s instanceof SliderSetting slider) slider.sliding = false;
                    if (s instanceof HueSetting hue) { hue.sliding = false; hue.colorSliding = false; }
                    if (s instanceof BindSettings bind) bind.active = false;
                    if (s instanceof StringSetting str) str.active = false;
                }
            }
        }
        currentSliderSetting = null;
    }

    public static float getBlocking(Module m) {
        return blockingAnimations.getOrDefault(m, 0f);
    }

    public static void setBlocking(Module m, float v) {
        if (v <= 0.001f) {
            blockingAnimations.remove(m);
            return;
        }
        blockingAnimations.put(m, v);
    }

    public static float getShake(Module m) {
        return shakeAnimations.getOrDefault(m, 0f);
    }

    public static void setShake(Module m, float v) {
        if (v <= 0.001f) {
            shakeAnimations.remove(m);
            return;
        }
        shakeAnimations.put(m, v);
    }

    // Setting heights ported from Rockstar's component getHeight() values
    public static float measureSettingHeight(Setting setting) {
        if (setting instanceof BooleanSetting s) return s.hidden.get() ? 0f : 18f;
        if (setting instanceof SliderSetting s) return s.hidden.get() ? 0f : 29f;
        if (setting instanceof ModeSetting s) {
            if (s.hidden.get()) return 0f;
            return 31f + s.modes.size() * 12f;
        }
        if (setting instanceof BindSettings s) return s.hidden.get() ? 0f : 19f;
        if (setting instanceof StringSetting s) return s.hidden.get() ? 0f : 35f;
        if (setting instanceof ButtonSetting s) return s.hidden.get() ? 0f : 24f;
        if (setting instanceof HueSetting s) {
            if (s.hidden.get()) return 0f;
            return 18f + (s.opened ? 78f : 0f);
        }
        if (setting instanceof MultiBooleanSetting s) {
            if (s.hidden.get()) return 0f;
            return 31f + s.settings.size() * 12f;
        }
        if (setting instanceof ListSetting s) {
            if (s.hidden.get()) return 0f;
            return 31f + s.list.size() * 12f;
        }
        return 0f;
    }

    private static String moduleSortKey(Module module) {
        String localized = module.getLocalizedName();
        if (localized != null && !localized.isBlank()) {
            return localized;
        }
        return module.name == null ? "" : module.name;
    }

    private static boolean containsQuery(String value, String query) {
        return normalizeSearch(value).contains(query);
    }

    private static String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
