package ru.strange.client.ui.clickgui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.impl.player.ShaderHand;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.ui.clickgui.GuiClient;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.ui.clickgui.localization.GuiLanguage;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;
import ru.strange.client.utils.math.ScrollUtil;
import ru.strange.client.utils.other.ItemShaderProfiles;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ItemShaderProfilesScreen extends Screen {
    private static final int PANEL_W = 560;
    private static final int PANEL_H = 338;
    private static final int PANEL_R = 10;
    private static final int PAD = 12;
    private static final int GAP = 10;
    private static final int HEADER_H = 48;
    private static final int FOOTER_H = 42;
    private static final int LEFT_W = 214;
    private static final int SEARCH_H = 22;
    private static final int SEARCH_TEXT_SIZE = 5;
    private static final int ROW_H = 24;
    private static final int THEME_H = 26;
    private static final int THEME_GAP = 6;
    private static final int THEME_COLUMNS = 2;

    private final Screen parent;
    private final ScrollUtil itemScroll = new ScrollUtil();
    private final ScrollUtil themeScroll = new ScrollUtil();
    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();

    private TextFieldWidget searchField;
    private String selectedItemId;
    private int panelX;
    private int panelY;

    public ItemShaderProfilesScreen(Screen parent) {
        super(Text.literal(localized("Шейдеры предметов", "Item Shader Profiles")));
        this.parent = parent;
        buildItemList();
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();

        searchField = new TextFieldWidget(this.textRenderer, panelX + PAD + 10, panelY + HEADER_H + 16, LEFT_W - 20, SEARCH_H, Text.literal(""));
        searchField.setMaxLength(72);
        searchField.setDrawsBackground(false);
        searchField.setEditableColor(0x00000000);
        searchField.setUneditableColor(0x00000000);
        searchField.setSuggestion(localized("Поиск по названию или id", "Search by name or id"));
        searchField.setSuggestion("");
        searchField.setPlaceholder(Text.empty());
        searchField.setChangedListener(value -> {
            applySearch();
            itemScroll.reset();
            ensureSelectionVisible();
            ensureSelectedThemeVisible(ItemShaderProfiles.snapshot());
        });
        addDrawableChild(searchField);

        applySearch();
        ensureSelection();
        ensureSelectionVisible();
        ensureSelectedThemeVisible(ItemShaderProfiles.snapshot());
        setInitialFocus(searchField);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String search = searchField == null ? "" : searchField.getText();
        boolean searchFocused = searchField != null && searchField.isFocused();
        String previousSelection = selectedItemId;
        super.resize(client, width, height);
        if (searchField != null) {
            searchField.setText(search);
            if (searchFocused) {
                focusSearch();
            } else {
                clearSearchFocus();
            }
        }
        selectedItemId = previousSelection;
        ensureSelection();
        ensureSelectionVisible();
        ensureSelectedThemeVisible(ItemShaderProfiles.snapshot());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()) {
            focusSearch();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SLASH && !hasControlDown()) {
            focusSearch();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && searchField != null && searchField.isFocused()) {
            clearSearchFocus();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateLayout() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
    }

    private void buildItemList() {
        allItems.clear();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }

            Identifier id = Registries.ITEM.getId(item);
            if (id == null) {
                continue;
            }

            ItemStack stack = new ItemStack(item);
            String displayName = stack.getName().getString();
            String itemId = id.toString();
            String searchKey = (displayName + " " + itemId).toLowerCase(Locale.ROOT);
            allItems.add(new ItemEntry(itemId, stack, displayName, searchKey));
        }

        allItems.sort(Comparator
                .comparing(ItemEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ItemEntry::itemId, String.CASE_INSENSITIVE_ORDER));
    }

    private void applySearch() {
        filteredItems.clear();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (ItemEntry entry : allItems) {
            if (query.isEmpty() || entry.searchKey().contains(query)) {
                filteredItems.add(entry);
            }
        }
        ensureSelection();
        ensureSelectionVisible();
    }

    private void ensureSelection() {
        if (filteredItems.isEmpty()) {
            selectedItemId = null;
            return;
        }

        for (ItemEntry entry : filteredItems) {
            if (entry.itemId().equals(selectedItemId)) {
                return;
            }
        }

        selectedItemId = filteredItems.getFirst().itemId();
    }

    private void ensureSelectionVisible() {
        if (selectedItemId == null || filteredItems.isEmpty()) {
            return;
        }

        RectArea list = listArea(new RectArea(panelX + PAD, panelY + HEADER_H + 8, LEFT_W, PANEL_H - HEADER_H - FOOTER_H - 16));
        int contentHeight = filteredItems.size() * ROW_H;
        itemScroll.setMax(contentHeight, list.height());

        int selectedIndex = -1;
        for (int i = 0; i < filteredItems.size(); i++) {
            if (filteredItems.get(i).itemId().equals(selectedItemId)) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex < 0) {
            return;
        }

        float rowTop = selectedIndex * ROW_H;
        float rowBottom = rowTop + ROW_H;
        float viewportTop = -itemScroll.getTarget();
        float viewportBottom = viewportTop + list.height();

        if (rowTop < viewportTop) {
            itemScroll.setTarget(-rowTop);
        } else if (rowBottom > viewportBottom) {
            itemScroll.setTarget(-(rowBottom - list.height()));
        }

        itemScroll.setTarget(Math.max(itemScroll.getMax(), Math.min(0.0f, itemScroll.getTarget())));
    }

    private void moveSelection(int delta) {
        if (filteredItems.isEmpty()) {
            return;
        }

        int index = 0;
        for (int i = 0; i < filteredItems.size(); i++) {
            if (filteredItems.get(i).itemId().equals(selectedItemId)) {
                index = i;
                break;
            }
        }

        int nextIndex = Math.max(0, Math.min(filteredItems.size() - 1, index + delta));
        selectedItemId = filteredItems.get(nextIndex).itemId();
        ensureSelectionVisible();
        ensureSelectedThemeVisible(ItemShaderProfiles.snapshot());
    }

    private void focusSearch() {
        if (searchField == null) {
            return;
        }

        searchField.setFocused(true);
        setFocused(searchField);
    }

    private void clearSearchFocus() {
        if (searchField == null) {
            return;
        }

        searchField.setFocused(false);
        if (getFocused() == searchField) {
            setFocused(null);
        }
    }

    private boolean isSearchClearVisible() {
        return searchField != null && !searchField.getText().isEmpty();
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        RectArea panel = panelArea();
        return GuiScreen.isHovered(mouseX, mouseY, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateLayout();
        itemScroll.update();
        themeScroll.update();
        Map<String, ItemShaderProfiles.ShaderProfile> profiles = ItemShaderProfiles.snapshot();

        int overlay = rgba(5, 8, 14, parent == null ? 110 : 132);
        context.fill(0, 0, this.width, this.height, overlay);

        int panelColor = panelColor();
        int sectionColor = sectionColor();
        int sectionAltColor = sectionAltColor();
        int accent = accentColor();
        int accentSoft = alpha(accent, 105);
        int text = textColor();
        int muted = mutedTextColor();
        int line = lineColor();

        RenderUtil.Blur.draw(context, panelX, panelY, PANEL_W, PANEL_H, PANEL_R, 12, alpha(accent, 22));
        RenderUtil.Shadow.draw(context, panelX - 3, panelY - 3, PANEL_W, PANEL_H, PANEL_R + 2, 18, rgba(0, 0, 0, 112));
        RenderUtil.Round.draw(context, panelX, panelY, PANEL_W, PANEL_H, PANEL_R, panelColor);
        RenderUtil.Border.draw(context, panelX, panelY, PANEL_W, PANEL_H, PANEL_R, 0.6f, line);
        RenderUtil.Round.draw(context, panelX + 10, panelY + 8, 108, 5, 2.5f, accentSoft);
        RenderUtil.Round.draw(context, panelX + 10, panelY + 8, 48, 5, 2.5f, accent);

        drawText(context, localized("Редактор шейдеров предметов", "Item Shader Editor"), panelX + PAD, panelY + 16, 8, text);
        drawText(context, localized("Отдельные темы для каждого предмета в руках", "Separate themes for each held item"), panelX + PAD, panelY + 29, 5, muted);

        float chipY = panelY + 16;
        String profileChip = localized("Профилей: ", "Profiles: ") + profiles.size();
        String itemsChip = localized("Предметов: ", "Items: ") + filteredItems.size();
        int itemsChipWidth = chipWidth(itemsChip);
        int profileChipWidth = chipWidth(profileChip);
        float itemsChipX = panelX + PANEL_W - PAD - itemsChipWidth;
        float profileChipX = itemsChipX - 8 - profileChipWidth;
        drawChip(context, profileChipX, chipY, profileChip, accentSoft, accent, text);
        drawChip(context, itemsChipX, chipY, itemsChip, sectionAltColor, line, text);

        RectArea left = leftArea();
        RectArea right = rightArea();
        RectArea preview = previewArea(right);
        RectArea themes = themesArea(right, preview);
        RectArea footer = footerArea();

        drawSection(context, left, sectionColor, line);
        drawSection(context, right, sectionAltColor, line);
        drawSearchBox(context, left, accent, text, muted);
        renderItemList(context, mouseX, mouseY, listArea(left), profiles, text, muted, accent, line);
        renderPreview(context, preview, profiles, text, muted, accent, line);
        renderThemeGrid(context, mouseX, mouseY, themes, profiles, text, accent);
        renderFooter(context, mouseX, mouseY, footer, profiles, text, muted, accent, line);

        super.render(context, mouseX, mouseY, delta);
        drawSearchFieldOverlay(context, searchArea(left), text, muted);
    }

    private void drawSection(DrawContext context, RectArea area, int background, int line) {
        RenderUtil.Blur.draw(context, area.x(), area.y(), area.width(), area.height(), 7, 10, alpha(background, 70));
        RenderUtil.Round.draw(context, area.x(), area.y(), area.width(), area.height(), 7, background);
        RenderUtil.Border.draw(context, area.x(), area.y(), area.width(), area.height(), 7, 0.45f, line);
    }

    private void drawSearchBox(DrawContext context, RectArea left, int accent, int text, int muted) {
        drawText(context, localized("Каталог предметов", "Item Catalog"), left.x() + 10, left.y() + 10, 6, text);
        drawText(context, localized("Выбери предмет и назначь тему", "Choose an item and assign a theme"), left.x() + 10, left.y() + 21, 4, muted);

        RectArea search = searchArea(left);
        RenderUtil.Round.draw(context, search.x(), search.y(), search.width(), search.height(), 5, alpha(sectionAltColor(), 232));
        RenderUtil.Border.draw(context, search.x(), search.y(), search.width(), search.height(), 5, 0.45f,
                searchField != null && searchField.isFocused() ? accent : lineColor());
        drawText(context, "/", search.x() + 8, search.y() + 8, 6, alpha(accent, 190));
        drawText(context, "Ctrl+F", search.x() + search.width() - 44, search.y() + 8, 4, muted);
        if (isSearchClearVisible()) {
            RectArea clear = searchClearArea(search);
            RenderUtil.Round.draw(context, clear.x(), clear.y(), clear.width(), clear.height(), 4, alpha(sectionColor(), 228));
            RenderUtil.Border.draw(context, clear.x(), clear.y(), clear.width(), clear.height(), 4, 0.45f, accent);
            drawCenteredText(context, "x", clear.x() + clear.width() / 2.0f, clear.y() + 9.5f, 5, text);
        }
        if (searchField != null) {
            searchField.setX(search.x() + 20);
            searchField.setY(search.y() + 4);
            searchField.setWidth(search.width() - (isSearchClearVisible() ? 52 : 48));
        }
    }

    private void drawSearchFieldOverlay(DrawContext context, RectArea search, int text, int muted) {
        if (searchField == null) {
            return;
        }

        String value = searchField.getText();
        float textX = search.x() + 24;
        float textY = search.y() + 12;
        float maxWidth = Math.max(12.0f, search.width() - (isSearchClearVisible() ? 80.0f : 76.0f));

        if (value.isEmpty()) {
            drawText(context, localized("Поиск по названию или id", "Search by name or id"), textX, textY, SEARCH_TEXT_SIZE, muted);
        } else {
            drawText(context, trimLeftToWidth(value, maxWidth, SEARCH_TEXT_SIZE), textX, textY, SEARCH_TEXT_SIZE, text);
        }

        if (searchField.isFocused() && ((System.currentTimeMillis() / 450L) & 1L) == 0L) {
            int cursor = Math.max(0, Math.min(searchField.getCursor(), value.length()));
            String beforeCursor = trimLeftToWidth(value.substring(0, cursor), maxWidth, SEARCH_TEXT_SIZE);
            float cursorX = textX + FontDraw.getWidth(FontDraw.FontType.MEDIUM, beforeCursor, SEARCH_TEXT_SIZE) + 1.0f;
            int caretX = Math.round(Math.min(textX + maxWidth, cursorX));
            context.fill(caretX, search.y() + 6, caretX + 1, search.y() + SEARCH_H - 6, alpha(text, 196));
        }
    }

    private void renderItemList(DrawContext context, int mouseX, int mouseY, RectArea area,
                                Map<String, ItemShaderProfiles.ShaderProfile> profiles,
                                int text, int muted, int accent, int line) {
        int contentHeight = filteredItems.size() * ROW_H;
        itemScroll.setMax(contentHeight, area.height());

        ScrollUtil.enable();
        ScrollUtil.scissor(this.client.getWindow(), area.x(), area.y(), area.width(), area.height());

        float scrollY = itemScroll.getScroll();
        for (int index = 0; index < filteredItems.size(); index++) {
            ItemEntry entry = filteredItems.get(index);
            int rowY = Math.round(area.y() + scrollY + index * ROW_H);
            if (rowY + ROW_H < area.y() || rowY > area.bottom()) {
                continue;
            }

            boolean selected = entry.itemId().equals(selectedItemId);
            boolean overridden = profiles.containsKey(entry.itemId());
            boolean hovered = GuiScreen.isHovered(mouseX, mouseY, area.x(), rowY, area.width() - 4, ROW_H - 3);

            int base = alpha(sectionAltColor(), hovered ? 238 : 212);
            if (overridden) {
                base = ShaderThemePreset.mixColors(base, accent, 0.18f);
            }
            if (selected) {
                base = ShaderThemePreset.mixColors(base, accent, 0.32f);
            }

            RenderUtil.Round.draw(context, area.x(), rowY, area.width() - 4, ROW_H - 3, 4, base);
            RenderUtil.Border.draw(context, area.x(), rowY, area.width() - 4, ROW_H - 3, 4, 0.45f, selected ? accent : line);
            context.drawItem(entry.stack(), area.x() + 5, rowY + 3);

            drawText(context, trimToWidth(entry.displayName(), area.width() - 60, 5), area.x() + 27, rowY + 8, 5, text);
            drawText(context, trimToWidth(entry.itemId(), area.width() - 60, 4), area.x() + 27, rowY + 16, 4, muted);

            if (overridden) {
                RenderUtil.Round.draw(context, area.x() + area.width() - 28, rowY + 7, 16, 8, 4, alpha(accent, 120));
                drawCenteredText(context, localized("ON", "ON"), area.x() + area.width() - 20, rowY + 13, 4, text);
            }
        }

        ScrollUtil.disable();

        if (filteredItems.isEmpty()) {
            drawCenteredText(context, localized("Ничего не найдено", "Nothing found"), area.x() + area.width() / 2.0f, area.y() + area.height() / 2.0f, 6, muted);
        }

        renderScrollbar(context, area.x() + area.width() - 2, area.y(), 3, area.height(), contentHeight, itemScroll.getScroll(), accent);
    }

    private void renderPreview(DrawContext context, RectArea area, Map<String, ItemShaderProfiles.ShaderProfile> profiles,
                               int text, int muted, int accent, int line) {
        RenderUtil.Round.draw(context, area.x(), area.y(), area.width(), area.height(), 7, alpha(sectionColor(), 238));
        RenderUtil.Border.draw(context, area.x(), area.y(), area.width(), area.height(), 7, 0.45f, line);

        ItemEntry selectedEntry = getSelectedEntry();
        if (selectedEntry == null) {
            drawText(context, localized("Предмет не выбран", "No item selected"), area.x() + 14, area.y() + 22, 7, text);
            drawText(context, localized("Слева выбери любой предмет из списка", "Select any item from the list on the left"), area.x() + 14, area.y() + 36, 4, muted);
            return;
        }

        ShaderThemePreset fallbackPreset = getFallbackTheme();
        ItemShaderProfiles.ShaderProfile profile = findProfile(profiles, selectedEntry.itemId());
        ShaderThemePreset preset = profile == null ? fallbackPreset : profile.resolveTheme(fallbackPreset);
        int themeColor = preset.accentColor();

        RenderUtil.Blur.draw(context, area.x() + 12, area.y() + 12, 44, 44, 10, 8, alpha(themeColor, 34));
        RenderUtil.Round.draw(context, area.x() + 12, area.y() + 12, 44, 44, 10, alpha(themeColor, 120));
        RenderUtil.Border.draw(context, area.x() + 12, area.y() + 12, 44, 44, 10, 0.5f, alpha(themeColor, 220));
        context.drawItem(selectedEntry.stack(), area.x() + 26, area.y() + 26);

        drawText(context, trimToWidth(selectedEntry.displayName(), area.width() - 138, 7), area.x() + 66, area.y() + 18, 7, text);
        drawText(context, trimToWidth(selectedEntry.itemId(), area.width() - 138, 4), area.x() + 66, area.y() + 31, 4, muted);

        String themeLabel = ModLocalization.raw(preset.displayName());
        drawChip(context, area.x() + 66, area.y() + 48, themeLabel, alpha(themeColor, 102), alpha(themeColor, 220), text);
        drawChip(context, area.x() + 66 + chipWidth(themeLabel) + 8, area.y() + 48,
                profile == null ? localized("Глобальная тема", "Global theme") : localized("Override активен", "Override active"),
                alpha(sectionAltColor(), 220), line, text);
    }

    private void renderThemeGrid(DrawContext context, int mouseX, int mouseY, RectArea area,
                                 Map<String, ItemShaderProfiles.ShaderProfile> profiles,
                                 int text, int accent) {
        drawText(context, localized("Темы шейдеров", "Shader Themes"), area.x() + 10, area.y() + 10, 6, text);
        drawText(context, localized("Кликни по карточке, чтобы применить её", "Click a card to apply it"), area.x() + 10, area.y() + 21, 4, mutedTextColor());

        RectArea grid = new RectArea(area.x() + 10, area.y() + 36, area.width() - 20, area.height() - 46);
        ShaderThemePreset[] presets = ShaderThemePreset.selectablePresets();
        int cellWidth = (grid.width() - THEME_GAP) / THEME_COLUMNS;
        int totalRows = (int) Math.ceil(presets.length / (double) THEME_COLUMNS);
        int totalHeight = totalRows * (THEME_H + THEME_GAP);
        themeScroll.setMax(totalHeight, grid.height());

        ScrollUtil.enable();
        ScrollUtil.scissor(this.client.getWindow(), grid.x(), grid.y(), grid.width(), grid.height());

        float scrollY = themeScroll.getScroll();
        ShaderThemePreset selectedTheme = getSelectedTheme(profiles);
        for (int index = 0; index < presets.length; index++) {
            ShaderThemePreset preset = presets[index];
            int col = index % THEME_COLUMNS;
            int row = index / THEME_COLUMNS;
            int cellX = grid.x() + col * (cellWidth + THEME_GAP);
            int cellY = Math.round(grid.y() + scrollY + row * (THEME_H + THEME_GAP));
            if (cellY + THEME_H < grid.y() || cellY > grid.bottom()) {
                continue;
            }

            boolean hovered = GuiScreen.isHovered(mouseX, mouseY, cellX, cellY, cellWidth, THEME_H);
            boolean selected = selectedTheme == preset;

            int background = ShaderThemePreset.mixColors(sectionAltColor(), preset.primaryColor(), hovered ? 0.38f : 0.24f);
            int border = selected ? accent : alpha(preset.accentColor(), hovered ? 255 : 210);

            RenderUtil.Round.draw(context, cellX, cellY, cellWidth, THEME_H, 5, alpha(background, hovered ? 244 : 228));
            RenderUtil.Border.draw(context, cellX, cellY, cellWidth, THEME_H, 5, 0.5f, border);
            RenderUtil.Round.draw(context, cellX + 6, cellY + 6, 14, 14, 4, alpha(preset.accentColor(), 210));
            drawText(context, trimToWidth(ModLocalization.raw(preset.displayName()), cellWidth - 32, 5), cellX + 26, cellY + 10, 5,
                    preset.isPulse() ? alpha(accent, 240) : text);
        }

        ScrollUtil.disable();
        renderScrollbar(context, grid.x() + grid.width() + 2, grid.y(), 3, grid.height(), totalHeight, themeScroll.getScroll(), accent);
    }

    private void renderFooter(DrawContext context, int mouseX, int mouseY, RectArea footer,
                              Map<String, ItemShaderProfiles.ShaderProfile> profiles,
                              int text, int muted, int accent, int line) {
        RenderUtil.Round.draw(context, footer.x(), footer.y(), footer.width(), footer.height(), 7, alpha(sectionColor(), 238));
        RenderUtil.Border.draw(context, footer.x(), footer.y(), footer.width(), footer.height(), 7, 0.45f, line);

        drawText(context, localized("Настройка хранится в item-shaders.json", "Settings are stored in item-shaders.json"), footer.x() + 10, footer.y() + 13, 5, muted);

        RectArea clear = clearButtonArea(footer);
        RectArea done = doneButtonArea(footer);
        drawActionButton(context, clear, mouseX, mouseY, localized("Сбросить профиль", "Clear Profile"), accent, hasProfileOverride(profiles, selectedItemId));
        drawActionButton(context, done, mouseX, mouseY, localized("Готово", "Done"), accent, true);
    }

    private void drawActionButton(DrawContext context, RectArea area, int mouseX, int mouseY, String text, int accent, boolean active) {
        boolean hovered = active && GuiScreen.isHovered(mouseX, mouseY, area.x(), area.y(), area.width(), area.height());
        int background = active
                ? ShaderThemePreset.mixColors(sectionAltColor(), accent, hovered ? 0.42f : 0.24f)
                : alpha(sectionAltColor(), 188);
        int border = active ? alpha(accent, hovered ? 255 : 210) : alpha(lineColor(), 150);
        int color = active ? textColor() : mutedTextColor();

        RenderUtil.Round.draw(context, area.x(), area.y(), area.width(), area.height(), 6, alpha(background, hovered ? 246 : 230));
        RenderUtil.Border.draw(context, area.x(), area.y(), area.width(), area.height(), 6, 0.5f, border);
        drawCenteredText(context, text, area.x() + area.width() / 2.0f, area.y() + 11.5f, 5, color);
    }

    private void drawChip(DrawContext context, float x, float y, String text, int background, int border, int color) {
        int width = chipWidth(text);
        RenderUtil.Round.draw(context, x, y, width, 16, 5, background);
        RenderUtil.Border.draw(context, x, y, width, 16, 5, 0.45f, border);
        drawCenteredText(context, text, x + width / 2.0f, y + 10.5f, 4, color);
    }

    private int chipWidth(String text) {
        return Math.max(58, (int) Math.ceil(FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 4) + 16));
    }

    private RectArea searchArea(RectArea left) {
        return new RectArea(left.x() + 10, left.y() + 34, left.width() - 20, SEARCH_H);
    }

    private RectArea searchClearArea(RectArea search) {
        return new RectArea(search.right() - 26, search.y() + 3, 18, 16);
    }

    private RectArea listArea(RectArea left) {
        RectArea search = searchArea(left);
        return new RectArea(left.x() + 10, search.bottom() + 8, left.width() - 16, left.height() - (search.bottom() - left.y()) - 18);
    }

    private RectArea panelArea() {
        return new RectArea(panelX, panelY, PANEL_W, PANEL_H);
    }

    private RectArea leftArea() {
        return new RectArea(panelX + PAD, panelY + HEADER_H + 8, LEFT_W, PANEL_H - HEADER_H - FOOTER_H - 16);
    }

    private RectArea rightArea() {
        RectArea left = leftArea();
        return new RectArea(left.right() + GAP, left.y(), PANEL_W - LEFT_W - PAD * 2 - GAP, left.height());
    }

    private RectArea previewArea(RectArea right) {
        return new RectArea(right.x(), right.y(), right.width(), 96);
    }

    private RectArea themesArea(RectArea right, RectArea preview) {
        return new RectArea(right.x(), preview.bottom() + GAP, right.width(), right.height() - preview.height() - GAP);
    }

    private RectArea themeGridArea(RectArea themes) {
        return new RectArea(themes.x() + 10, themes.y() + 36, themes.width() - 20, themes.height() - 46);
    }

    private RectArea footerArea() {
        return new RectArea(panelX + PAD, panelY + PANEL_H - FOOTER_H - 4, PANEL_W - PAD * 2, FOOTER_H);
    }

    private RectArea clearButtonArea(RectArea footer) {
        return new RectArea(footer.right() - 220, footer.y() + 8, 112, 24);
    }

    private RectArea doneButtonArea(RectArea footer) {
        return new RectArea(footer.right() - 98, footer.y() + 8, 88, 24);
    }

    private ItemEntry getSelectedEntry() {
        if (selectedItemId == null) {
            return null;
        }

        for (ItemEntry entry : filteredItems) {
            if (entry.itemId().equals(selectedItemId)) {
                return entry;
            }
        }

        for (ItemEntry entry : allItems) {
            if (entry.itemId().equals(selectedItemId)) {
                return entry;
            }
        }

        return null;
    }

    private ShaderThemePreset getSelectedTheme(Map<String, ItemShaderProfiles.ShaderProfile> profiles) {
        ShaderThemePreset fallbackPreset = getFallbackTheme();
        ItemShaderProfiles.ShaderProfile profile = findProfile(profiles, selectedItemId);
        return profile == null ? fallbackPreset : profile.resolveTheme(fallbackPreset);
    }

    private void ensureSelectedThemeVisible(Map<String, ItemShaderProfiles.ShaderProfile> profiles) {
        if (selectedItemId == null) {
            return;
        }

        ShaderThemePreset[] presets = ShaderThemePreset.selectablePresets();
        ShaderThemePreset selectedTheme = getSelectedTheme(profiles);
        int selectedIndex = -1;
        for (int index = 0; index < presets.length; index++) {
            if (presets[index] == selectedTheme) {
                selectedIndex = index;
                break;
            }
        }

        if (selectedIndex < 0) {
            return;
        }

        RectArea right = rightArea();
        RectArea preview = previewArea(right);
        RectArea themeGrid = themeGridArea(themesArea(right, preview));
        int totalRows = (int) Math.ceil(presets.length / (double) THEME_COLUMNS);
        int totalHeight = totalRows * (THEME_H + THEME_GAP);
        themeScroll.setMax(totalHeight, themeGrid.height());

        int row = selectedIndex / THEME_COLUMNS;
        float rowTop = row * (THEME_H + THEME_GAP);
        float rowBottom = rowTop + THEME_H;
        float viewportTop = -themeScroll.getTarget();
        float viewportBottom = viewportTop + themeGrid.height();

        if (rowTop < viewportTop) {
            themeScroll.setTarget(-rowTop);
        } else if (rowBottom > viewportBottom) {
            themeScroll.setTarget(-(rowBottom - themeGrid.height()));
        }

        themeScroll.setTarget(Math.max(themeScroll.getMax(), Math.min(0.0f, themeScroll.getTarget())));
    }

    private void clearSelectedProfile() {
        if (ItemShaderProfiles.hasOverride(selectedItemId)) {
            ItemShaderProfiles.clear(selectedItemId);
        }
    }

    private ShaderThemePreset getFallbackTheme() {
        return ShaderHand.getConfiguredShaderPreset();
    }

    private ItemShaderProfiles.ShaderProfile findProfile(Map<String, ItemShaderProfiles.ShaderProfile> profiles, String itemId) {
        if (profiles == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        return profiles.get(itemId);
    }

    private boolean hasProfileOverride(Map<String, ItemShaderProfiles.ShaderProfile> profiles, String itemId) {
        return findProfile(profiles, itemId) != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button != 0) {
            return false;
        }

        if (!isInsidePanel(mouseX, mouseY)) {
            close();
            return true;
        }

        RectArea left = leftArea();
        RectArea search = searchArea(left);
        if (isSearchClearVisible()) {
            RectArea clearSearch = searchClearArea(search);
            if (GuiScreen.isHovered(mouseX, mouseY, clearSearch.x(), clearSearch.y(), clearSearch.width(), clearSearch.height())) {
                searchField.setText("");
                focusSearch();
                return true;
            }
        }

        if (!GuiScreen.isHovered(mouseX, mouseY, search.x(), search.y(), search.width(), search.height())) {
            clearSearchFocus();
        }

        RectArea list = listArea(left);
        if (GuiScreen.isHovered(mouseX, mouseY, list.x(), list.y(), list.width(), list.height())) {
            float scrollY = itemScroll.getScroll();
            for (int index = 0; index < filteredItems.size(); index++) {
                int rowY = Math.round(list.y() + scrollY + index * ROW_H);
                if (GuiScreen.isHovered(mouseX, mouseY, list.x(), rowY, list.width() - 4, ROW_H - 3)) {
                    selectedItemId = filteredItems.get(index).itemId();
                    ensureSelectionVisible();
                    ensureSelectedThemeVisible(ItemShaderProfiles.snapshot());
                    return true;
                }
            }
        }

        RectArea right = rightArea();
        RectArea preview = previewArea(right);
        RectArea themes = themesArea(right, preview);
        RectArea themeGrid = themeGridArea(themes);
        if (selectedItemId != null && GuiScreen.isHovered(mouseX, mouseY, themeGrid.x(), themeGrid.y(), themeGrid.width(), themeGrid.height())) {
            ShaderThemePreset preset = resolveThemeAt(mouseX, mouseY, themeGrid);
            if (preset != null) {
                ItemShaderProfiles.setTheme(selectedItemId, preset);
                ensureSelectedThemeVisible(ItemShaderProfiles.snapshot());
                return true;
            }
        }

        RectArea footer = footerArea();
        RectArea clear = clearButtonArea(footer);
        RectArea done = doneButtonArea(footer);
        if (GuiScreen.isHovered(mouseX, mouseY, clear.x(), clear.y(), clear.width(), clear.height())) {
            if (ItemShaderProfiles.hasOverride(selectedItemId)) {
                clearSelectedProfile();
                ensureSelectedThemeVisible(ItemShaderProfiles.snapshot());
                return true;
            }
        }
        if (GuiScreen.isHovered(mouseX, mouseY, done.x(), done.y(), done.width(), done.height())) {
            close();
            return true;
        }

        return false;
    }

    private ShaderThemePreset resolveThemeAt(double mouseX, double mouseY, RectArea grid) {
        ShaderThemePreset[] presets = ShaderThemePreset.selectablePresets();
        int cellWidth = (grid.width() - THEME_GAP) / THEME_COLUMNS;
        float scrollY = themeScroll.getScroll();

        for (int index = 0; index < presets.length; index++) {
            int col = index % THEME_COLUMNS;
            int row = index / THEME_COLUMNS;
            int cellX = grid.x() + col * (cellWidth + THEME_GAP);
            int cellY = Math.round(grid.y() + scrollY + row * (THEME_H + THEME_GAP));
            if (GuiScreen.isHovered(mouseX, mouseY, cellX, cellY, cellWidth, THEME_H)) {
                return presets[index];
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        RectArea left = leftArea();
        RectArea list = listArea(left);
        if (GuiScreen.isHovered(mouseX, mouseY, list.x(), list.y(), list.width(), list.height())) {
            itemScroll.handleScroll(verticalAmount);
            return true;
        }

        RectArea right = rightArea();
        RectArea preview = previewArea(right);
        RectArea themes = themesArea(right, preview);
        RectArea themeGrid = themeGridArea(themes);
        if (GuiScreen.isHovered(mouseX, mouseY, themeGrid.x(), themeGrid.y(), themeGrid.width(), themeGrid.height())) {
            themeScroll.handleScroll(verticalAmount);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        clearSearchFocus();
        MinecraftClient client = this.client;
        if (client != null) {
            client.setScreen(parent != null ? parent : new GuiClient());
        }
    }

    private int panelColor() {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 232);
    }

    private int sectionColor() {
        return RenderUtil.ColorUtil.replAlpha(ShaderThemePreset.mixColors(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 0xFF000000, 0.08f), 232);
    }

    private int sectionAltColor() {
        return RenderUtil.ColorUtil.replAlpha(ShaderThemePreset.mixColors(RenderUtil.ColorUtil.getBackGroundColor(1, 1), RenderUtil.ColorUtil.getMainColor(1, 1), 0.07f), 236);
    }

    private int accentColor() {
        return RenderUtil.ColorUtil.getMainColor(1, 1);
    }

    private int textColor() {
        return RenderUtil.ColorUtil.getTextColor(1, 1);
    }

    private int mutedTextColor() {
        return alpha(textColor(), 164);
    }

    private int lineColor() {
        return alpha(textColor(), 34);
    }

    private int alpha(int color, int alpha) {
        return RenderUtil.ColorUtil.replAlpha(color, alpha);
    }

    private void renderScrollbar(DrawContext context, int x, int y, int width, int height, int contentHeight, float scroll, int accent) {
        if (contentHeight <= height) {
            return;
        }

        RenderUtil.Round.draw(context, x, y, width, height, 1.5f, rgba(255, 255, 255, 18));
        float visibleRatio = Math.max(0.14f, height / (float) contentHeight);
        float thumbHeight = Math.max(18.0f, height * visibleRatio);
        float maxScroll = Math.max(1.0f, contentHeight - height);
        float progress = Math.max(0.0f, Math.min(1.0f, -scroll / maxScroll));
        float thumbY = y + (height - thumbHeight) * progress;
        RenderUtil.Round.draw(context, x, thumbY, width, thumbHeight, 1.5f, alpha(accent, 220));
    }

    private void drawText(DrawContext context, String text, float x, float y, int size, int color) {
        FontDraw.drawText(FontDraw.FontType.MEDIUM, context, text, x, y, size, color);
    }

    private void drawCenteredText(DrawContext context, String text, float x, float y, int size, int color) {
        FontDraw.drawCenter(FontDraw.FontType.MEDIUM, context, text, x, y, size, color, false);
    }

    private String trimToWidth(String text, float maxWidth, int size) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, size) <= maxWidth) {
            return text;
        }

        String current = text;
        while (current.length() > 1 && FontDraw.getWidth(FontDraw.FontType.MEDIUM, current + "...", size) > maxWidth) {
            current = current.substring(0, current.length() - 1);
        }
        return current + "...";
    }

    private String trimLeftToWidth(String text, float maxWidth, int size) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String current = text;
        while (!current.isEmpty() && FontDraw.getWidth(FontDraw.FontType.MEDIUM, current, size) > maxWidth) {
            current = current.substring(1);
        }
        return current;
    }

    private static int rgba(int r, int g, int b, int a) {
        return new Color(r, g, b, a).getRGB();
    }

    private static String localized(String ru, String en) {
        return GuiLocalization.currentLanguage() == GuiLanguage.RU ? ru : en;
    }

    private record ItemEntry(String itemId, ItemStack stack, String displayName, String searchKey) {
    }

    private record RectArea(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }
    }
}
