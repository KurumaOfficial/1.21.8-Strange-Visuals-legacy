package ru.strange.client.StarterMenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.Strange;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.io.File;
import java.util.List;

public class AltManagerScreen extends Screen {
    private static final FontDraw.FontType FONT = FontDraw.FontType.MEDIUM;
    private static final int MAX_ALT_NAME_LENGTH = 16;

    private static final File ALTS_FILE = new File(Strange.root, "alts.json");

    private static final int PANEL_W = 224;
    private static final int PANEL_H = 198;
    private static final int PANEL_R = 8;
    private static final int HEADER_H = 23;

    private static final int COLS = 2;
    private static final int VISIBLE_ROWS = 5;
    private static final int CARD_H = 24;
    private static final int CARD_R = 6;
    private static final int GRID_PAD_L = 6;
    private static final int GRID_PAD_R = 7;
    private static final int GRID_GAP_X = 4;
    private static final int GRID_GAP_Y = 4;
    private static final int SCROLL_W = 3;

    private static final int BOTTOM_GAP = 7;
    private static final int BOTTOM_H = 20;
    private static final int LEFT_GROUP_W = 124;
    private static final int LEFT_GROUP_R = 7;
    private static final int DELETE_GAP = 6;
    private static final int BOTTOM_ACTION_GAP = 4;
    private static final int INPUT_SEG_W = 88;
    private static final int RANDOM_SEG_W = 15;
    private static final int SORT_H = 16;
    private static final int SORT_ITEM_H = 14;
    private static final int TRASH_PANEL_W = 148;

    private static final int TRASH_VISIBLE = 4;
    private static final int TOAST_TICKS = 90;

    private static final int COL_PANEL_BG = rgba(32, 32, 32, 236);
    private static final int COL_PANEL_LINE = rgba(255, 255, 255, 16);
    private static final int COL_TITLE = rgba(239, 239, 239, 255);
    private static final int COL_HEAD_TEXT = rgba(191, 191, 191, 255);
    private static final int COL_HEAD_CUR = rgba(255, 220, 120, 255);
    private static final int COL_SORT_BG = rgba(44, 44, 44, 220);
    private static final int COL_SORT_HOV = rgba(61, 61, 61, 232);
    private static final int COL_CARD_BG = rgba(39, 39, 39, 214);
    private static final int COL_CARD_HOV = rgba(50, 50, 50, 226);
    private static final int COL_CARD_SEL = rgba(61, 61, 61, 236);
    private static final int COL_CARD_LINE = rgba(255, 255, 255, 12);
    private static final int COL_CARD_SEL_LINE = rgba(255, 220, 120, 90);
    private static final int COL_TEXT = rgba(243, 243, 243, 255);
    private static final int COL_TEXT_DIM = rgba(210, 210, 210, 245);
    private static final int COL_PLACEHOLDER = rgba(144, 144, 144, 190);
    private static final int COL_GROUP_BG = rgba(43, 43, 43, 225);
    private static final int COL_GROUP_HOV = rgba(57, 57, 57, 232);
    private static final int COL_GROUP_LINE = rgba(255, 255, 255, 12);
    private static final int COL_PLUS_BG = rgba(96, 96, 96, 228);
    private static final int COL_PLUS_BG_HOV = rgba(120, 120, 120, 236);
    private static final int COL_PLUS_TEXT = rgba(250, 250, 250, 255);
    private static final int COL_DELETE_BG = rgba(92, 46, 46, 190);
    private static final int COL_DELETE_BG_HOV = rgba(118, 58, 58, 212);
    private static final int COL_DELETE_LINE = rgba(255, 255, 255, 10);
    private static final int COL_DELETE_TEXT = rgba(255, 220, 220, 255);
    private static final int COL_SCROLL_TRACK = rgba(255, 255, 255, 12);
    private static final int COL_SCROLL_THUMB = rgba(201, 201, 201, 228);
    private static final int COL_TOAST_BG = rgba(30, 30, 30, 232);
    private static final int COL_TOAST_TEXT = rgba(229, 229, 229, 255);
    private static final int COL_TRASH_BG = rgba(36, 36, 36, 228);
    private static final int COL_TRASH_HOV = rgba(58, 58, 58, 228);

    private static final Identifier PERSON_TEX = Strange.id("icons/gui/person.png");
    private static final Identifier MENU_TEX = Strange.id("icons/gui/menu.png");
    private static final Identifier PLUS_TEX = Strange.id("icons/gui/plus.png");
    private static final Identifier DICE_TEX = Strange.id("icons/gui/dice.png");
    private static final Identifier STAR_TEX = Strange.id("icons/gui/star.png");
    private static final Identifier CLOSE_TEX = Strange.id("icons/gui/close.png");
    private static final Identifier BIN_TEX = Strange.id("icons/gui/rubbish-bin.png");
    private static final Identifier ARROW_TEX = Strange.id("icons/gui/arrow.png");

    private final Screen parent;
    private final AltAccountController accountController = new AltAccountController(new AltAccountStore(ALTS_FILE));
    private final AltSessionService sessionService = new AltSessionService();
    private final List<AltAccount> accounts = accountController.activeAccounts();
    private final List<AltAccount> deletedAccounts = accountController.deletedAccounts();
    private final float[] cardHover = new float[COLS * VISIBLE_ROWS];

    private int selectedIdx = -1;
    private int scroll;
    private float scrollSmooth;
    private float alpha;
    private AltSortMode sortMode = AltSortMode.NEWEST;
    private boolean sortDropdownOpen;
    private boolean trashOpen;
    private int trashScroll;
    private String inputText = "";
    private boolean inputFocused;
    private int cursorBlink;
    private String toast;
    private int toastTick;
    private int px;
    private int py;

    private int sortDropdownIndex = -1;
    private int trashSelectedIdx = -1;

    private record UiStateSnapshot(int selectedIdx, int scroll, float scrollSmooth, int trashScroll, int trashSelectedIdx) {
    }

    private record SortDropdownLayout(int x, int y, int width, int height) {
        int itemY(int index) {
            return y + 2 + index * SORT_ITEM_H;
        }

        boolean contains(double mouseX, double mouseY) {
            return inRect(mouseX, mouseY, x, y, width, height);
        }

        boolean containsItem(double mouseX, double mouseY, int index) {
            return inRect(mouseX, mouseY, x + 2, itemY(index), width - 4, SORT_ITEM_H);
        }
    }

    private record TrashPanelLayout(int x, int y, int width, int height, int visibleCount) {
        int itemY(int visibleIndex) {
            return y + 26 + visibleIndex * 20;
        }

        boolean contains(double mouseX, double mouseY) {
            return inRect(mouseX, mouseY, x, y, width, height);
        }

        boolean containsClose(double mouseX, double mouseY) {
            return inRect(mouseX, mouseY, x + width - 16, y + 5, 10, 10);
        }

        boolean containsItem(double mouseX, double mouseY, int visibleIndex) {
            return inRect(mouseX, mouseY, x + 5, itemY(visibleIndex), width - 10, 16);
        }

        boolean containsDelete(double mouseX, double mouseY, int visibleIndex) {
            return inRect(mouseX, mouseY, x + width - 34, itemY(visibleIndex) + 2, 12, 12);
        }

        boolean containsRestore(double mouseX, double mouseY, int visibleIndex) {
            return inRect(mouseX, mouseY, x + width - 21, itemY(visibleIndex) + 2, 12, 12);
        }
    }

    public AltManagerScreen(Screen parent) {
        super(Text.literal(MenuLocalization.tr("alt.title")));
        this.parent = parent;
        loadAccounts();
    }

    private static int rgba(int r, int g, int b, int a) {
        return ((Math.max(0, Math.min(255, a)) & 0xFF) << 24)
                | ((Math.max(0, Math.min(255, r)) & 0xFF) << 16)
                | ((Math.max(0, Math.min(255, g)) & 0xFF) << 8)
                | (Math.max(0, Math.min(255, b)) & 0xFF);
    }

    private static int mixAlpha(int color, float multiplier) {
        int alpha = (color >> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        int mixedAlpha = Math.max(0, Math.min(255, Math.round(alpha * multiplier)));
        return (mixedAlpha << 24) | rgb;
    }

    private static int lerpColor(int first, int second, float delta) {
        int a1 = (first >> 24) & 0xFF;
        int r1 = (first >> 16) & 0xFF;
        int g1 = (first >> 8) & 0xFF;
        int b1 = first & 0xFF;

        int a2 = (second >> 24) & 0xFF;
        int r2 = (second >> 16) & 0xFF;
        int g2 = (second >> 8) & 0xFF;
        int b2 = second & 0xFF;

        int a = (int) (a1 + (a2 - a1) * delta);
        int r = (int) (r1 + (r2 - r1) * delta);
        int g = (int) (g1 + (g2 - g1) * delta);
        int b = (int) (b1 + (b2 - b1) * delta);
        return rgba(r, g, b, a);
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static boolean isNickChar(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    private static int totalBlockHeight() {
        return PANEL_H + BOTTOM_GAP + BOTTOM_H;
    }

    private int cardWidth() {
        return (PANEL_W - GRID_PAD_L - GRID_PAD_R - SCROLL_W - GRID_GAP_X) / 2;
    }

    private int bottomY() {
        return py + PANEL_H + BOTTOM_GAP;
    }

    private int deleteButtonWidth() {
        return (rightActionWidth() - BOTTOM_ACTION_GAP) / 2;
    }

    private int trashButtonWidth() {
        return rightActionWidth() - deleteButtonWidth() - BOTTOM_ACTION_GAP;
    }

    private int rightActionWidth() {
        return PANEL_W - LEFT_GROUP_W - DELETE_GAP;
    }

    private int trashTabX() {
        return px + LEFT_GROUP_W + DELETE_GAP + deleteButtonWidth() + BOTTOM_ACTION_GAP;
    }

    private int trashTabY() {
        return bottomY();
    }

    private int trashPanelX() {
        int preferredX = px + PANEL_W - TRASH_PANEL_W;
        return Math.max(6, Math.min(preferredX, Math.max(6, width - TRASH_PANEL_W - 6)));
    }

    private int trashPanelY() {
        int preferredY = bottomY() - trashPanelHeight() - 6;
        return Math.max(6, Math.min(preferredY, Math.max(6, height - trashPanelHeight() - 6)));
    }

    private int trashPanelHeight() {
        int itemCount = Math.max(1, Math.min(TRASH_VISIBLE, deletedAccounts.size()));
        return 26 + itemCount * 20;
    }

    private int sortWidth() {
        float widestLabel = textWidth(currentSortLabel(), 5);
        for (AltSortMode mode : AltSortMode.values()) {
            widestLabel = Math.max(widestLabel, textWidth(mode.label(), 5));
        }
        return Math.max(64, (int) widestLabel + 20);
    }

    private int sortX() {
        return px + PANEL_W - sortWidth() - 6;
    }

    private int sortY() {
        return py + 5;
    }

    private int sortDropdownY() {
        int preferredY = sortY() + SORT_H + 4;
        return Math.max(4, Math.min(preferredY, Math.max(4, height - sortDropdownHeight() - 4)));
    }

    private int sortDropdownHeight() {
        return AltSortMode.values().length * SORT_ITEM_H + 4;
    }

    private boolean isInsideSortButton(double mouseX, double mouseY) {
        return inRect(mouseX, mouseY, sortX(), sortY(), sortWidth(), SORT_H);
    }

    private boolean isInsideSortDropdown(double mouseX, double mouseY) {
        return sortDropdownLayout().contains(mouseX, mouseY);
    }

    private int listX() {
        return px + GRID_PAD_L;
    }

    private int listY() {
        return py + HEADER_H + 5;
    }

    private int listInteractiveWidth() {
        return PANEL_W - GRID_PAD_L - GRID_PAD_R;
    }

    private int listHeight() {
        return VISIBLE_ROWS * CARD_H + (VISIBLE_ROWS - 1) * GRID_GAP_Y;
    }

    private boolean isInsideListArea(double mouseX, double mouseY) {
        return inRect(mouseX, mouseY, listX(), listY(), listInteractiveWidth(), listHeight());
    }

    private boolean isInsideTrashPanel(double mouseX, double mouseY) {
        return trashPanelLayout().contains(mouseX, mouseY);
    }

    private SortDropdownLayout sortDropdownLayout() {
        return new SortDropdownLayout(sortX(), sortDropdownY(), sortWidth(), sortDropdownHeight());
    }

    private TrashPanelLayout trashPanelLayout() {
        int visibleCount = Math.min(TRASH_VISIBLE, deletedAccounts.size());
        return new TrashPanelLayout(trashPanelX(), trashPanelY(), TRASH_PANEL_W, trashPanelHeight(), visibleCount);
    }

    private void blurInputField() {
        inputFocused = false;
        cursorBlink = 0;
    }

    private void focusInputField() {
        closeSortDropdown();
        closeTrashPanel();
        inputFocused = true;
        cursorBlink = 0;
    }

    private boolean dismissTransientUi(boolean dismissInput) {
        boolean changed = false;
        if (sortDropdownOpen) {
            closeSortDropdown();
            changed = true;
        }
        if (trashOpen) {
            closeTrashPanel();
            changed = true;
        }
        if (dismissInput && inputFocused) {
            inputFocused = false;
            changed = true;
        }
        return changed;
    }

    private float textWidth(String text, int size) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        return FontDraw.getWidth(FONT, text, size);
    }

    private void drawText(DrawContext context, String text, float x, float y, int size, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        FontDraw.drawText(FONT, context, text, x, y, size, color);
    }

    private void drawCenteredText(DrawContext context, String text, float x, float y, int size, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        FontDraw.drawCenter(FONT, context, text, x, y, size, color, false);
    }

    private float midTextY(float centerY) {
        return centerY + 2.0f;
    }

    private void drawMidText(DrawContext context, String text, float x, float centerY, int size, int color) {
        drawText(context, text, x, midTextY(centerY), size, color);
    }

    private void drawCenteredMidText(DrawContext context, String text, float centerX, float centerY, int size, int color) {
        drawCenteredText(context, text, centerX, midTextY(centerY), size, color);
    }

    private String trimToWidth(String text, float maxWidth, int size) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
            return "";
        }
        if (textWidth(text, size) <= maxWidth) {
            return text;
        }

        String current = text;
        while (current.length() > 1 && textWidth(current + "..", size) > maxWidth) {
            current = current.substring(0, current.length() - 1);
        }
        return current + "..";
    }

    private String trimLeftToWidth(String text, float maxWidth, int size) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
            return "";
        }

        String current = text;
        while (!current.isEmpty() && textWidth(current, size) > maxWidth) {
            current = current.substring(1);
        }
        return current;
    }

    private String getSelectedName() {
        if (selectedIdx >= 0 && selectedIdx < accounts.size()) {
            return accounts.get(selectedIdx).name;
        }
        return null;
    }

    private String currentSortLabel() {
        return sortMode.label();
    }

    private int alphaColor(int color, int alpha) {
        return RenderUtil.ColorUtil.replAlpha(color, alpha);
    }

    private int mixColor(int first, int second, float amount) {
        return ShaderThemePreset.mixColors(first, second, amount);
    }

    private int themeBaseColor() {
        return RenderUtil.ColorUtil.getBackGroundColor(1, 1);
    }

    private int themeAccentColor() {
        return RenderUtil.ColorUtil.getMainColor(1, 1);
    }

    private int themeTextColor() {
        return RenderUtil.ColorUtil.getTextColor(1, 1);
    }

    private int themeMutedTextColor() {
        return alphaColor(themeTextColor(), 176);
    }

    private int themePlaceholderColor() {
        return alphaColor(themeTextColor(), 120);
    }

    private int themePanelColor() {
        return alphaColor(themeBaseColor(), 236);
    }

    private int themeSectionColor() {
        return alphaColor(mixColor(themeBaseColor(), 0xFF000000, 0.10f), 230);
    }

    private int themeElevatedColor() {
        return alphaColor(mixColor(themeBaseColor(), themeAccentColor(), 0.08f), 236);
    }

    private int themeHoverColor() {
        return alphaColor(mixColor(themeBaseColor(), themeAccentColor(), 0.18f), 238);
    }

    private int themeLineColor() {
        return alphaColor(themeTextColor(), 34);
    }

    private int themeAccentSoftColor() {
        return alphaColor(themeAccentColor(), 110);
    }

    private int themeCurrentColor() {
        return alphaColor(mixColor(themeTextColor(), themeAccentColor(), 0.60f), 255);
    }

    private int themeDangerAccentColor() {
        return alphaColor(mixColor(themeAccentColor(), rgba(255, 92, 92, 255), 0.72f), 255);
    }

    private int themeDangerSurfaceColor(boolean hovered) {
        return alphaColor(mixColor(themeElevatedColor(), themeDangerAccentColor(), hovered ? 0.34f : 0.24f), hovered ? 240 : 224);
    }

    private int themeDangerTextColor() {
        return alphaColor(mixColor(themeTextColor(), themeDangerAccentColor(), 0.32f), 245);
    }

    private int themeBadgeDangerColor() {
        return alphaColor(mixColor(themeAccentColor(), rgba(255, 76, 96, 255), 0.82f), 246);
    }

    @Override
    protected void init() {
        MenuLocalization.initialize();
        MenuBackgroundManager.refresh();
        alpha = 0.0f;
        px = width / 2 - PANEL_W / 2;
        py = height / 2 - totalBlockHeight() / 2 + 4;
        closeSortDropdown();
        closeTrashPanel();
        inputFocused = false;
        scrollSmooth = scroll;
        syncSelectionToCurrentSession();
        ensureValidSelection();
    }

    @Override
    public void tick() {
        super.tick();
        if (toastTick > 0) {
            toastTick--;
        }
        cursorBlink++;
        scrollSmooth += (scroll - scrollSmooth) * 0.14f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        alpha = Math.min(1.0f, alpha + 0.05f);

        drawBackground(context);
        drawShapes(context, mouseX, mouseY, delta);
        drawTextLayer(context);
    }

    private void drawBackground(DrawContext context) {
        float overlayAlpha = alpha;
        int overlayColor = alphaColor(mixColor(rgba(5, 8, 13, 255), themeAccentColor(), 0.06f), 154);
        context.fill(0, 0, width, height, mixAlpha(overlayColor, overlayAlpha));

        for (int i = 0; i < 42; i++) {
            int edgeAlpha = (int) ((42 - i) * 0.75f * overlayAlpha);
            context.fill(0, i, width, i + 1, rgba(0, 0, 0, edgeAlpha));
            context.fill(0, height - 1 - i, width, height - i, rgba(0, 0, 0, edgeAlpha));
        }

        for (int i = 0; i < 64; i++) {
            int edgeAlpha = (int) ((64 - i) * 0.45f * overlayAlpha);
            context.fill(i, 0, i + 1, height, rgba(0, 0, 0, edgeAlpha));
            context.fill(width - 1 - i, 0, width - i, height, rgba(0, 0, 0, edgeAlpha));
        }
    }

    private void drawShapes(DrawContext context, int mouseX, int mouseY, float delta) {
        float drawAlpha = alpha;
        int accent = themeAccentColor();
        int accentSoft = themeAccentSoftColor();
        int text = themeTextColor();
        int muted = themeMutedTextColor();
        int panelColor = themePanelColor();
        int sectionColor = themeSectionColor();
        int elevatedColor = themeElevatedColor();
        int hoverColor = themeHoverColor();
        int line = themeLineColor();
        int currentColor = themeCurrentColor();
        int dangerAccent = themeDangerAccentColor();

        RenderUtil.Blur.draw(context, px, py, PANEL_W, PANEL_H, PANEL_R, 12, mixAlpha(alphaColor(accent, 28), drawAlpha));
        RenderUtil.Shadow.draw(context, px - 3, py - 3, PANEL_W, PANEL_H, PANEL_R + 2, 18, mixAlpha(rgba(0, 0, 0, 112), drawAlpha));
        RenderUtil.Round.draw(context, px, py, PANEL_W, PANEL_H, PANEL_R, mixAlpha(panelColor, drawAlpha));
        RenderUtil.Border.draw(context, px, py, PANEL_W, PANEL_H, PANEL_R, 0.45f, mixAlpha(line, drawAlpha));
        RenderUtil.Round.draw(context, px + 10, py + 8, 74, 4, 2.5f, mixAlpha(accentSoft, drawAlpha));
        RenderUtil.Round.draw(context, px + 10, py + 8, 28, 4, 2.5f, mixAlpha(accent, drawAlpha));

        int dividerY = py + HEADER_H;
        context.fill(px + 6, dividerY, px + PANEL_W - 6, dividerY + 1, mixAlpha(alphaColor(text, 10), drawAlpha));

        String sortLabel = currentSortLabel();
        int sortW = Math.max(64, (int) textWidth(sortLabel, 5) + 20);
        int sortH = SORT_H;
        int sortX = sortX();
        int sortY = sortY();
        boolean sortHover = inRect(mouseX, mouseY, sortX, sortY, sortW, sortH);
        boolean sortActive = sortHover || sortDropdownOpen;
        int sortColor = alphaColor(mixColor(elevatedColor, accent, sortActive ? 0.18f : 0.08f), sortActive ? 242 : 228);

        RenderUtil.Round.draw(context, sortX, sortY, sortW, sortH, 5, mixAlpha(sortColor, drawAlpha));
        RenderUtil.Border.draw(context, sortX, sortY, sortW, sortH, 5, 0.45f,
                mixAlpha(sortDropdownOpen ? accentSoft : line, drawAlpha));
        RenderUtil.Image.draw(context, MENU_TEX, sortX + sortW - 12, sortY + 4, 8, 8,
                imgColor(alphaColor(sortDropdownOpen ? accent : muted, 214), drawAlpha));

        int listX = px + GRID_PAD_L;
        int listY = py + HEADER_H + 5;
        int cardWidth = cardWidth();
        int totalRows = (accounts.size() + COLS - 1) / COLS;
        String liveName = getCurrentName();

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (scroll + row) * COLS + col;
                int slot = row * COLS + col;
                int cardX = listX + col * (cardWidth + GRID_GAP_X);
                int cardY = listY + row * (CARD_H + GRID_GAP_Y);

                if (index >= accounts.size()) {
                    if (slot < cardHover.length) {
                        cardHover[slot] = Math.max(0.0f, cardHover[slot] - delta * 0.35f);
                    }
                    continue;
                }

                boolean hovered = inRect(mouseX, mouseY, cardX, cardY, cardWidth, CARD_H);
                boolean selected = index == selectedIdx;
                boolean current = accounts.get(index).name.equalsIgnoreCase(liveName);
                if (slot < cardHover.length) {
                    cardHover[slot] = hovered
                            ? Math.min(1.0f, cardHover[slot] + delta * 0.45f)
                            : Math.max(0.0f, cardHover[slot] - delta * 0.45f);
                }
                float hoverDelta = slot < cardHover.length ? cardHover[slot] : 0.0f;

                int cardBase = selected
                        ? alphaColor(mixColor(elevatedColor, accent, 0.40f), 248)
                        : current
                        ? alphaColor(mixColor(sectionColor, accent, 0.16f), 230)
                        : sectionColor;
                int cardColor = selected ? cardBase : lerpColor(cardBase, hoverColor, hoverDelta * 0.82f);
                int borderColor = selected ? alphaColor(accent, 172) : current ? alphaColor(currentColor, 118) : line;

                if (selected) {
                    RenderUtil.Shadow.draw(context, cardX - 1, cardY - 1, cardWidth, CARD_H, CARD_R + 1, 10,
                            mixAlpha(alphaColor(accent, 78), drawAlpha));
                }

                RenderUtil.Round.draw(context, cardX, cardY, cardWidth, CARD_H, CARD_R, mixAlpha(cardColor, drawAlpha));
                RenderUtil.Border.draw(context, cardX, cardY, cardWidth, CARD_H, CARD_R, 0.45f, mixAlpha(borderColor, drawAlpha));
                if (selected) {
                    RenderUtil.Round.draw(context, cardX + 1, cardY + 1, 3, CARD_H - 2, 2,
                            mixAlpha(alphaColor(accent, 246), drawAlpha));
                } else if (current) {
                    RenderUtil.Round.draw(context, cardX + 1, cardY + 4, 2, CARD_H - 8, 1.5f,
                            mixAlpha(alphaColor(currentColor, 214), drawAlpha));
                }

                int avatarX = cardX + 4;
                int avatarY = cardY + 4;
                RenderUtil.Round.draw(context, avatarX, avatarY, 16, 16, 4,
                        mixAlpha(alphaColor(mixColor(sectionColor, accent, selected ? 0.36f : (current ? 0.20f : 0.10f)), 226), drawAlpha));
                RenderUtil.Image.draw(context, PERSON_TEX, avatarX + 1, avatarY + 1, 14, 14,
                        imgColor(alphaColor(text, 240), drawAlpha));

                int removeX = cardX + cardWidth - 20;
                int removeY = cardY + 8;
                boolean removeHover = inRect(mouseX, mouseY, removeX - 2, removeY - 2, 10, 10);
                RenderUtil.Image.draw(context, CLOSE_TEX, removeX, removeY, 7, 7,
                        imgColor(alphaColor(dangerAccent, removeHover ? 255 : 182), drawAlpha));

                int pinX = cardX + cardWidth - 10;
                int pinY = cardY + 8;
                boolean pinHover = inRect(mouseX, mouseY, pinX - 2, pinY - 2, 10, 10);
                RenderUtil.Image.draw(context, STAR_TEX, pinX, pinY, 7, 7,
                        imgColor(alphaColor(accounts.get(index).pinned
                                ? accent
                                : (pinHover ? text : muted), accounts.get(index).pinned ? 255 : (pinHover ? 235 : 184)), drawAlpha));
            }
        }

        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        if (maxScroll > 0) {
            int trackX = px + PANEL_W - 7;
            int trackY = listY;
            int trackH = PANEL_H - HEADER_H - 12;
            float scrollDelta = maxScroll == 0 ? 0.0f : scrollSmooth / maxScroll;
            int thumbH = Math.max(22, (int) (trackH * ((float) VISIBLE_ROWS / totalRows)));
            int thumbY = trackY + (int) ((trackH - thumbH) * scrollDelta);

            RenderUtil.Round.draw(context, trackX, trackY, SCROLL_W, trackH, 2, mixAlpha(alphaColor(text, 18), drawAlpha));
            RenderUtil.Round.draw(context, trackX, thumbY, SCROLL_W, thumbH, 2, mixAlpha(alphaColor(accent, 224), drawAlpha));
            RenderUtil.Border.draw(context, trackX - 1, thumbY, SCROLL_W + 2, thumbH, 2, 0.35f, mixAlpha(accentSoft, drawAlpha));
        }

        if (sortDropdownOpen) {
            AltSortMode[] modes = AltSortMode.values();
            SortDropdownLayout layout = sortDropdownLayout();
            int dropdownX = layout.x();
            int dropdownY = layout.y();
            int dropdownH = layout.height();

            RenderUtil.Blur.draw(context, dropdownX, dropdownY, sortW, dropdownH, 5, 10, mixAlpha(alphaColor(accent, 18), drawAlpha));
            RenderUtil.Shadow.draw(context, dropdownX - 2, dropdownY - 2, sortW, dropdownH, 7, 12, mixAlpha(rgba(0, 0, 0, 96), drawAlpha));
            RenderUtil.Round.draw(context, dropdownX, dropdownY, sortW, dropdownH, 5, mixAlpha(sectionColor, drawAlpha));
            RenderUtil.Border.draw(context, dropdownX, dropdownY, sortW, dropdownH, 5, 0.45f, mixAlpha(line, drawAlpha));

            for (int i = 0; i < modes.length; i++) {
                int itemY = layout.itemY(i);
                boolean hovered = layout.containsItem(mouseX, mouseY, i);
                boolean selected = i == sortDropdownIndex;
                if (hovered || selected || modes[i] == sortMode) {
                    RenderUtil.Round.draw(context, dropdownX + 2, itemY, sortW - 4, SORT_ITEM_H, 3,
                            mixAlpha(alphaColor(mixColor(elevatedColor, accent,
                                            selected ? 0.28f : (modes[i] == sortMode ? 0.22f : 0.14f)),
                                    selected ? 246 : (modes[i] == sortMode ? 238 : 220)), drawAlpha));
                }
            }
        }

        if (trashOpen) {
            TrashPanelLayout layout = trashPanelLayout();
            int panelX = layout.x();
            int panelY = layout.y();
            int panelW = layout.width();
            int visibleCount = layout.visibleCount();
            int panelH = layout.height();

            RenderUtil.Blur.draw(context, panelX, panelY, panelW, panelH, 7, 10, mixAlpha(alphaColor(accent, 18), drawAlpha));
            RenderUtil.Shadow.draw(context, panelX - 2, panelY - 2, panelW, panelH, 8, 12, mixAlpha(rgba(0, 0, 0, 96), drawAlpha));
            RenderUtil.Round.draw(context, panelX, panelY, panelW, panelH, 7, mixAlpha(sectionColor, drawAlpha));
            RenderUtil.Border.draw(context, panelX, panelY, panelW, panelH, 7, 0.45f, mixAlpha(line, drawAlpha));
            context.fill(panelX + 8, panelY + 22, panelX + panelW - 8, panelY + 23, mixAlpha(alphaColor(text, 8), drawAlpha));

            boolean closeHover = layout.containsClose(mouseX, mouseY);
            RenderUtil.Image.draw(context, CLOSE_TEX, panelX + panelW - 13, panelY + 8, 6, 6,
                    imgColor(alphaColor(closeHover ? text : muted, closeHover ? 240 : 190), drawAlpha));

            for (int i = 0; i < visibleCount; i++) {
                int deletedIndex = trashScroll + i;
                if (deletedIndex >= deletedAccounts.size()) {
                    break;
                }
                int itemY = layout.itemY(i);
                boolean hovered = layout.containsItem(mouseX, mouseY, i);
                boolean selected = deletedIndex == trashSelectedIdx;
                RenderUtil.Round.draw(context, panelX + 5, itemY, panelW - 10, 16, 5,
                        mixAlpha(alphaColor(mixColor(elevatedColor, accent, selected ? 0.26f : (hovered ? 0.18f : 0.08f)),
                                selected ? 242 : (hovered ? 236 : 220)), drawAlpha));
                RenderUtil.Image.draw(context, PERSON_TEX, panelX + 9, itemY + 2, 12, 12,
                        imgColor(alphaColor(text, 216), drawAlpha));

                boolean arrowHover = layout.containsRestore(mouseX, mouseY, i);
                RenderUtil.Image.draw(context, ARROW_TEX, panelX + panelW - 20, itemY + 4, 7, 7,
                        imgColor(alphaColor(arrowHover ? accent : muted, arrowHover ? 240 : 210), drawAlpha));

                boolean deleteHover = layout.containsDelete(mouseX, mouseY, i);
                RenderUtil.Image.draw(context, CLOSE_TEX, panelX + panelW - 32, itemY + 5, 6, 6,
                        imgColor(alphaColor(dangerAccent, deleteHover ? 255 : 176), drawAlpha));
            }

            if (deletedAccounts.size() > TRASH_VISIBLE) {
                int scrollTrackH = panelH - 28;
                int scrollThumbH = Math.max(8, scrollTrackH * TRASH_VISIBLE / deletedAccounts.size());
                int scrollMax = deletedAccounts.size() - TRASH_VISIBLE;
                float scrollDelta = scrollMax > 0 ? (float) trashScroll / scrollMax : 0.0f;
                int scrollX = panelX + panelW - 5;
                int scrollY = panelY + 24 + (int) ((scrollTrackH - scrollThumbH) * scrollDelta);
                RenderUtil.Round.draw(context, scrollX, scrollY, 2, scrollThumbH, 1, mixAlpha(alphaColor(accent, 210), drawAlpha));
            }
        }

        drawBottomShapes(context, mouseX, mouseY);
        drawToastShapes(context);
    }

    private void drawBottomShapes(DrawContext context, int mouseX, int mouseY) {
        float drawAlpha = alpha;
        int accent = themeAccentColor();
        int accentSoft = themeAccentSoftColor();
        int text = themeTextColor();
        int muted = themeMutedTextColor();
        int sectionColor = themeSectionColor();
        int elevatedColor = themeElevatedColor();
        int line = themeLineColor();
        int groupY = bottomY();
        int groupX = px;
        int groupW = LEFT_GROUP_W;
        int groupH = BOTTOM_H;

        boolean groupHover = inRect(mouseX, mouseY, groupX, groupY, groupW, groupH);

        RenderUtil.Blur.draw(context, groupX, groupY, groupW, groupH, LEFT_GROUP_R, 9, mixAlpha(alphaColor(accent, 14), drawAlpha));
        RenderUtil.Shadow.draw(context, groupX - 2, groupY - 2, groupW, groupH, LEFT_GROUP_R + 1, 10, mixAlpha(rgba(0, 0, 0, 86), drawAlpha));
        RenderUtil.Round.draw(context, groupX, groupY, groupW, groupH, LEFT_GROUP_R,
                mixAlpha(groupHover || inputFocused ? themeHoverColor() : elevatedColor, drawAlpha));
        RenderUtil.Border.draw(context, groupX, groupY, groupW, groupH, LEFT_GROUP_R, 0.45f,
                mixAlpha(inputFocused ? accentSoft : line, drawAlpha));

        int separator1 = groupX + INPUT_SEG_W;
        int separator2 = separator1 + RANDOM_SEG_W;
        context.fill(separator1, groupY + 4, separator1 + 1, groupY + groupH - 4, mixAlpha(alphaColor(text, 12), drawAlpha));
        context.fill(separator2, groupY + 4, separator2 + 1, groupY + groupH - 4, mixAlpha(alphaColor(text, 12), drawAlpha));

        boolean randomHover = inRect(mouseX, mouseY, separator1, groupY, RANDOM_SEG_W, groupH);
        if (randomHover) {
            RenderUtil.Round.draw(context, separator1 + 1, groupY + 2, RANDOM_SEG_W - 2, groupH - 4, 4,
                    mixAlpha(alphaColor(mixColor(sectionColor, accent, 0.18f), 132), drawAlpha));
        }

        int addX = separator2 + 2;
        int addY = groupY + 2;
        int addW = groupX + groupW - 2 - addX;
        int addH = groupH - 4;
        boolean addHover = inRect(mouseX, mouseY, separator2, groupY, groupW - (separator2 - groupX), groupH);

        RenderUtil.Round.draw(context, addX, addY, addW, addH, 5,
                mixAlpha(alphaColor(mixColor(accent, elevatedColor, addHover ? 0.28f : 0.18f), addHover ? 244 : 232), drawAlpha));
        RenderUtil.Border.draw(context, addX, addY, addW, addH, 5, 0.45f,
                mixAlpha(addHover ? accentSoft : line, drawAlpha));

        RenderUtil.Image.draw(context, PLUS_TEX, addX + addW / 2.0f - 4, groupY + groupH / 2.0f - 4, 8, 8,
                imgColor(alphaColor(text, 255), drawAlpha));
        RenderUtil.Image.draw(context, DICE_TEX, separator1 + RANDOM_SEG_W / 2.0f - 3.5f, groupY + groupH / 2.0f - 3.5f, 7, 7,
                imgColor(alphaColor(randomHover ? accent : muted, randomHover ? 235 : 205), drawAlpha));

        int deleteX = px + LEFT_GROUP_W + DELETE_GAP;
        int deleteW = deleteButtonWidth();
        boolean deleteHover = inRect(mouseX, mouseY, deleteX, groupY, deleteW, groupH);

        RenderUtil.Blur.draw(context, deleteX, groupY, deleteW, groupH, 7, 9, mixAlpha(alphaColor(themeDangerAccentColor(), 12), drawAlpha));
        RenderUtil.Shadow.draw(context, deleteX - 2, groupY - 2, deleteW, groupH, 8, 10, mixAlpha(rgba(0, 0, 0, 76), drawAlpha));
        RenderUtil.Round.draw(context, deleteX, groupY, deleteW, groupH, 7,
                mixAlpha(themeDangerSurfaceColor(deleteHover), drawAlpha));
        RenderUtil.Border.draw(context, deleteX, groupY, deleteW, groupH, 7, 0.45f,
                mixAlpha(deleteHover ? alphaColor(themeDangerAccentColor(), 124) : line, drawAlpha));

        int trashX = trashTabX();
        int trashW = trashButtonWidth();
        boolean trashHover = inRect(mouseX, mouseY, trashX, groupY, trashW, groupH);
        RenderUtil.Blur.draw(context, trashX, groupY, trashW, groupH, 7, 9, mixAlpha(alphaColor(accent, 12), drawAlpha));
        RenderUtil.Shadow.draw(context, trashX - 2, groupY - 2, trashW, groupH, 8, 10, mixAlpha(rgba(0, 0, 0, 76), drawAlpha));
        RenderUtil.Round.draw(context, trashX, groupY, trashW, groupH, 7,
                mixAlpha(alphaColor(mixColor(elevatedColor, accent, trashHover || trashOpen ? 0.18f : 0.08f), trashHover || trashOpen ? 238 : 228), drawAlpha));
        RenderUtil.Border.draw(context, trashX, groupY, trashW, groupH, 7, 0.45f,
                mixAlpha(trashOpen ? accentSoft : line, drawAlpha));
        if (!deletedAccounts.isEmpty()) {
            int badgeX = trashX + trashW - 8;
            int badgeY = groupY + 3;
            RenderUtil.Round.draw(context, badgeX, badgeY, 5, 5, 3, mixAlpha(themeBadgeDangerColor(), drawAlpha));
        }

        if (inputFocused && (cursorBlink / 14) % 2 == 0) {
            String visible = trimLeftToWidth(inputText, INPUT_SEG_W - 12, 6);
            int cursorX = groupX + 6 + Math.round(textWidth(visible, 6));
            if (cursorX < groupX + INPUT_SEG_W - 6) {
                context.fill(cursorX, groupY + 6, cursorX + 1, groupY + groupH - 6, mixAlpha(alphaColor(text, 184), drawAlpha));
            }
        }
    }

    private void drawToastShapes(DrawContext context) {
        if (toast == null || toastTick <= 0) {
            return;
        }

        float drawAlpha = alpha * Math.min(1.0f, toastTick / 20.0f);
        int toastWidth = (int) textWidth(toast, 4) + 24;
        int toastHeight = 19;
        int toastX = width / 2 - toastWidth / 2;
        int toastY = bottomY() + BOTTOM_H + 8;

        RenderUtil.Blur.draw(context, toastX, toastY, toastWidth, toastHeight, 5, 8, mixAlpha(alphaColor(themeAccentColor(), 14), drawAlpha));
        RenderUtil.Shadow.draw(context, toastX - 2, toastY - 2, toastWidth, toastHeight, 6, 10, mixAlpha(rgba(0, 0, 0, 86), drawAlpha));
        RenderUtil.Round.draw(context, toastX, toastY, toastWidth, toastHeight, 5,
                mixAlpha(alphaColor(mixColor(themeSectionColor(), themeAccentColor(), 0.10f), 236), drawAlpha));
        RenderUtil.Border.draw(context, toastX, toastY, toastWidth, toastHeight, 5, 0.45f, mixAlpha(themeAccentSoftColor(), drawAlpha));
    }

    private void drawTextLayer(DrawContext context) {
        float drawAlpha = alpha;
        int accent = themeAccentColor();
        int text = themeTextColor();
        int muted = themeMutedTextColor();
        int placeholder = themePlaceholderColor();
        int currentColor = themeCurrentColor();

        drawCenteredMidText(context, MenuLocalization.tr("alt.title"), width / 2.0f, py - 12, 8, mixAlpha(text, drawAlpha));

        float headerCenterY = py + HEADER_H / 2.0f;
        String countText = MenuLocalization.tr("alt.total_profiles", accounts.size());
        drawMidText(context, countText, px + 8, headerCenterY, 5, mixAlpha(muted, drawAlpha));

        String sortLabel = currentSortLabel();
        int sortW = sortWidth();
        int sortX = sortX();
        float sortCenterX = sortX + (sortW - 12) / 2.0f - 1.0f;
        drawCenteredMidText(context, sortLabel, sortCenterX, py + 13, 5,
                mixAlpha(sortDropdownOpen ? currentColor : muted, drawAlpha));

        int currentX = px + 80;
        String currentPrefix = MenuLocalization.tr("alt.current_session") + " - ";
        float prefixWidth = textWidth(currentPrefix, 5);
        float freeWidth = sortX - 6 - currentX - prefixWidth;
        String currentName = trimToWidth(getCurrentName(), Math.max(20.0f, freeWidth), 5);

        drawMidText(context, currentPrefix, currentX, headerCenterY, 5, mixAlpha(muted, drawAlpha));
        drawMidText(context, currentName, currentX + prefixWidth, headerCenterY, 5, mixAlpha(currentColor, drawAlpha));

        int listX = px + GRID_PAD_L;
        int listY = py + HEADER_H + 5;
        int cardWidth = cardWidth();
        String liveName = getCurrentName();

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (scroll + row) * COLS + col;
                if (index >= accounts.size()) {
                    continue;
                }

                int cardX = listX + col * (cardWidth + GRID_GAP_X);
                int cardY = listY + row * (CARD_H + GRID_GAP_Y);
                boolean selected = index == selectedIdx;
                boolean current = accounts.get(index).name.equalsIgnoreCase(liveName);
                int nameOffset = selected ? 30 : 23;
                String name = trimToWidth(accounts.get(index).name, cardWidth - (nameOffset + 21), 4);

                int textColor = selected ? text : current ? currentColor : muted;
                if (selected) {
                    RenderUtil.Round.draw(context, cardX + 23, cardY + 10, 4, 4, 2, mixAlpha(alphaColor(accent, 255), drawAlpha));
                } else if (current) {
                    RenderUtil.Round.draw(context, cardX + 23, cardY + 10, 4, 4, 2, mixAlpha(alphaColor(currentColor, 230), drawAlpha));
                }
                drawMidText(context, name, cardX + nameOffset, cardY + CARD_H / 2.0f, 4, mixAlpha(textColor, drawAlpha));
            }
        }

        if (accounts.isEmpty()) {
            drawCenteredMidText(context, MenuLocalization.tr("alt.no_profiles"), px + PANEL_W / 2.0f, py + PANEL_H / 2.0f + 2, 4,
                    mixAlpha(placeholder, drawAlpha));
        }

        if (sortDropdownOpen) {
            AltSortMode[] modes = AltSortMode.values();
            SortDropdownLayout layout = sortDropdownLayout();
            int dropdownX = layout.x();
            int dropdownY = layout.y();
            for (int i = 0; i < modes.length; i++) {
                int itemY = layout.itemY(i);
                int color = i == sortDropdownIndex
                        ? mixAlpha(currentColor, drawAlpha)
                        : modes[i] == sortMode ? mixAlpha(alphaColor(currentColor, 220), drawAlpha) : mixAlpha(text, drawAlpha);
                drawCenteredMidText(context, modes[i].label(), dropdownX + sortW / 2.0f, itemY + SORT_ITEM_H / 2.0f, 4, color);
            }
        }

        if (trashOpen) {
            TrashPanelLayout layout = trashPanelLayout();
            int panelX = layout.x();
            int panelY = layout.y();
            int panelW = layout.width();
            drawCenteredMidText(context, MenuLocalization.tr("alt.deleted_profiles"), panelX + panelW / 2.0f, panelY + 11, 5, mixAlpha(text, drawAlpha));

            if (deletedAccounts.isEmpty()) {
                drawCenteredMidText(context, MenuLocalization.tr("alt.deleted_empty"), panelX + panelW / 2.0f, panelY + 36, 4, mixAlpha(placeholder, drawAlpha));
            } else {
                int visibleCount = layout.visibleCount();
                for (int i = 0; i < visibleCount; i++) {
                    int deletedIndex = trashScroll + i;
                    if (deletedIndex >= deletedAccounts.size()) {
                        break;
                    }
                    int itemY = layout.itemY(i);
                    String name = trimToWidth(deletedAccounts.get(deletedIndex).name, panelW - 42, 4);
                    int rowColor = deletedIndex == trashSelectedIdx ? mixAlpha(text, drawAlpha) : mixAlpha(muted, drawAlpha);
                    drawMidText(context, name, panelX + 23, itemY + 8, 4, rowColor);
                }
            }
        }

        int groupY = bottomY();
        if (inputText.isEmpty()) {
            drawMidText(context, MenuLocalization.tr("alt.nickname"), px + 6, groupY + BOTTOM_H / 2.0f, 5, mixAlpha(placeholder, drawAlpha));
        } else {
            String visible = trimLeftToWidth(inputText, INPUT_SEG_W - 12, 6);
            if (!visible.isEmpty()) {
                drawMidText(context, visible, px + 6, groupY + BOTTOM_H / 2.0f, 6, mixAlpha(text, drawAlpha));
            }
        }

        int deleteX = px + LEFT_GROUP_W + DELETE_GAP;
        int deleteW = deleteButtonWidth();
        drawCenteredMidText(context, MenuLocalization.tr("alt.delete_all"), deleteX + deleteW / 2.0f, groupY + BOTTOM_H / 2.0f, 5,
                mixAlpha(themeDangerTextColor(), drawAlpha));

        int trashX = trashTabX();
        int trashW = trashButtonWidth();
        String trashLabel = trimToWidth(MenuLocalization.tr("alt.deleted_button"), Math.max(24.0f, trashW - 8), 4);
        drawCenteredMidText(context, trashLabel, trashX + trashW / 2.0f, groupY + BOTTOM_H / 2.0f, 4,
                mixAlpha(trashOpen ? alphaColor(accent, 232) : muted, drawAlpha));

        drawToastText(context);
    }

    private void drawToastText(DrawContext context) {
        if (toast == null || toastTick <= 0) {
            return;
        }

        float drawAlpha = alpha * Math.min(1.0f, toastTick / 20.0f);
        int toastWidth = (int) textWidth(toast, 4) + 24;
        int toastX = width / 2 - toastWidth / 2;
        int toastY = bottomY() + BOTTOM_H + 8;
        drawCenteredMidText(context, toast, toastX + toastWidth / 2.0f, toastY + 9.5f, 4, mixAlpha(themeTextColor(), drawAlpha));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (sortDropdownOpen) {
            blurInputField();
            AltSortMode[] modes = AltSortMode.values();
            SortDropdownLayout layout = sortDropdownLayout();
            for (int i = 0; i < modes.length; i++) {
                if (layout.containsItem(mouseX, mouseY, i)) {
                    sortDropdownIndex = i;
                    closeSortDropdown();
                    applySortMode(modes[i]);
                    return true;
                }
            }

            if (isInsideSortButton(mouseX, mouseY)) {
                closeSortDropdown();
                return true;
            }

            if (isInsideSortDropdown(mouseX, mouseY)) {
                return true;
            }

            closeSortDropdown();
        }

        if (isInsideSortButton(mouseX, mouseY)) {
            openSortDropdown();
            return true;
        }

        if (inRect(mouseX, mouseY, trashTabX(), trashTabY(), trashButtonWidth(), BOTTOM_H)) {
            toggleTrashPanel();
            return true;
        }

        if (trashOpen) {
            blurInputField();
            if (isInsideTrashPanel(mouseX, mouseY)) {
                TrashPanelLayout layout = trashPanelLayout();
                if (layout.containsClose(mouseX, mouseY)) {
                    closeTrashPanel();
                    return true;
                }

                int visibleCount = layout.visibleCount();
                for (int i = 0; i < visibleCount; i++) {
                    int deletedIndex = trashScroll + i;
                    if (deletedIndex >= deletedAccounts.size()) {
                        break;
                    }

                    if (layout.containsDelete(mouseX, mouseY, i)) {
                        trashSelectedIdx = deletedIndex;
                        deleteDeletedPermanently(deletedIndex);
                        return true;
                    }

                    if (layout.containsRestore(mouseX, mouseY, i)) {
                        trashSelectedIdx = deletedIndex;
                        restoreDeletedAccount(deletedIndex);
                        return true;
                    }

                    if (layout.containsItem(mouseX, mouseY, i)) {
                        if (trashSelectedIdx != deletedIndex) {
                            trashSelectedIdx = deletedIndex;
                            ensureTrashSelectionVisible();
                            return true;
                        }
                        restoreDeletedAccount(deletedIndex);
                        return true;
                    }
                }
                return true;
            }

            if (!inRect(mouseX, mouseY, trashTabX(), trashTabY(), trashButtonWidth(), BOTTOM_H)) {
                closeTrashPanel();
                return true;
            }
        }

        int groupY = bottomY();
        int groupX = px;
        int groupW = LEFT_GROUP_W;
        int groupH = BOTTOM_H;
        int separator1 = groupX + INPUT_SEG_W;
        int separator2 = separator1 + RANDOM_SEG_W;
        int deleteX = px + LEFT_GROUP_W + DELETE_GAP;
        int deleteW = deleteButtonWidth();

        if (inRect(mouseX, mouseY, groupX, groupY, INPUT_SEG_W, groupH)) {
            focusInputField();
            return true;
        }

        if (inRect(mouseX, mouseY, separator1, groupY, RANDOM_SEG_W, groupH)) {
            blurInputField();
            randomNick();
            return true;
        }

        if (inRect(mouseX, mouseY, separator2, groupY, groupW - (separator2 - groupX), groupH)) {
            blurInputField();
            addAccount();
            return true;
        }

        if (inRect(mouseX, mouseY, deleteX, groupY, deleteW, groupH)) {
            blurInputField();
            clearActiveAccounts();
            return true;
        }

        int listX = listX();
        int listY = listY();
        int cardWidth = cardWidth();

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (scroll + row) * COLS + col;
                if (index >= accounts.size()) {
                    continue;
                }

                int cardX = listX + col * (cardWidth + GRID_GAP_X);
                int cardY = listY + row * (CARD_H + GRID_GAP_Y);
                if (!inRect(mouseX, mouseY, cardX, cardY, cardWidth, CARD_H)) {
                    continue;
                }

                blurInputField();

                int removeX = cardX + cardWidth - 21;
                int removeY = cardY + 6;
                if (inRect(mouseX, mouseY, removeX, removeY, 10, 10)) {
                    moveAccountToDeleted(index);
                    return true;
                }

                int pinX = cardX + cardWidth - 13;
                int pinY = cardY + 6;
                if (inRect(mouseX, mouseY, pinX, pinY, 10, 10)) {
                    togglePinned(index);
                    return true;
                }

                selectedIdx = index;
                useSelectedProfile(accounts.get(index));
                return true;
            }
        }

        blurInputField();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (sortDropdownOpen) {
            int direction = (int) Math.signum(verticalAmount);
            if (direction != 0) {
                AltSortMode[] modes = AltSortMode.values();
                int currentIndex = sortDropdownIndex < 0 ? sortMode.ordinal() : sortDropdownIndex;
                sortDropdownIndex = Math.max(0, Math.min(modes.length - 1, currentIndex - direction));
            }
            return true;
        }

        if (trashOpen) {
            if (isInsideTrashPanel(mouseX, mouseY)) {
                int maxScroll = Math.max(0, deletedAccounts.size() - TRASH_VISIBLE);
                trashScroll = Math.max(0, Math.min(trashScroll - (int) Math.signum(verticalAmount), maxScroll));
                snapTrashSelectionToVisibleWindow();
            }
            return true;
        }

        if (!isInsideListArea(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int totalRows = (accounts.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(verticalAmount), maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (dismissTransientUi(true)) {
                return true;
            }
            closeToParent();
            return true;
        }

        if (sortDropdownOpen) {
            return handleSortDropdownKeyPressed(keyCode);
        }

        if (trashOpen) {
            return handleTrashPanelKeyPressed(keyCode);
        }

        if (inputFocused) {
            return handleFocusedInputKeyPressed(keyCode);
        }

        if (handleGlobalShortcutKeyPressed(keyCode)) {
            return true;
        }

        if (handleSelectionNavigationKey(keyCode)) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (selectedIdx >= 0 && selectedIdx < accounts.size()) {
                useSelectedProfile(accounts.get(selectedIdx));
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE && selectedIdx >= 0 && selectedIdx < accounts.size()) {
            moveAccountToDeleted(selectedIdx);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputFocused) {
            appendAltNameCharacters(Character.toString(chr));
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        closeToParent();
    }

    private void addAccount() {
        String name = sessionService.normalizeName(inputText);
        if (name == null) {
            showToast(MenuLocalization.tr("alt.invalid_name_hint"));
            return;
        }

        if (accountController.containsName(name)) {
            showToast(MenuLocalization.tr("alt.exists"));
            return;
        }

        AltAccountController.Snapshot snapshot = accountController.snapshot();
        UiStateSnapshot previousUiState = captureUiState();
        AltAccount account = accountController.createAccount(name, sortMode);
        selectActiveAccountByName(account.name);
        scrollSelectionIntoView();
        if (!persistAccounts(snapshot, previousUiState)) {
            return;
        }
        inputText = "";
        showToast(MenuLocalization.tr("alt.added", account.name));
    }

    private void randomNick() {
        String nick;
        int tries = 0;
        do {
            nick = NickGenerator.generate();
            tries++;
        } while (accountController.containsName(nick) && tries < 25);

        if (accountController.containsName(nick)) {
            showToast(MenuLocalization.tr("alt.exists"));
            return;
        }

        AltAccountController.Snapshot snapshot = accountController.snapshot();
        UiStateSnapshot previousUiState = captureUiState();
        AltAccount account = accountController.createAccount(nick, sortMode);
        selectActiveAccountByName(account.name);
        scrollSelectionIntoView();
        if (!persistAccounts(snapshot, previousUiState)) {
            return;
        }
        showToast(MenuLocalization.tr("alt.generated", account.name));
    }

    private void applySortMode(AltSortMode newSortMode) {
        if (newSortMode == null || newSortMode == sortMode) {
            return;
        }

        AltAccountController.Snapshot snapshot = accountController.snapshot();
        UiStateSnapshot previousUiState = captureUiState();
        AltSortMode previousSortMode = sortMode;
        String selectedName = getSelectedName();
        sortMode = newSortMode;
        accountController.sortActive(sortMode);
        selectedIdx = accountController.findActiveIndex(selectedName);
        ensureValidSelection();
        if (!persistAccounts(snapshot, previousUiState)) {
            sortMode = previousSortMode;
        }
    }

    private void clampScroll() {
        int totalRows = (accounts.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private void useSelectedProfile(AltAccount account) {
        if (account == null) {
            return;
        }

        AltAccountController.Snapshot snapshot = accountController.snapshot();
        AltSessionService.SessionSnapshot previousSession = sessionService.captureSession(client);
        UiStateSnapshot previousUiState = captureUiState();

        AltSessionService.SwitchResult result;
        if (sessionService.isCurrentProfile(client, account.name)) {
            result = AltSessionService.SwitchResult.SUCCESS;
        } else {
            result = sessionService.switchToOfflineProfile(client, account.name);
        }

        switch (result) {
            case SUCCESS -> {
                accountController.setSelectedActiveName(account.name);
                if (!persistAccounts(snapshot, previousUiState)) {
                    sessionService.restoreSession(client, previousSession);
                    syncSelectionToCurrentSession();
                    ensureValidSelection();
                    return;
                }
                syncSelectionToCurrentSession();
                ensureValidSelection();
                showToast(MenuLocalization.tr("alt.changed", account.name));
            }
            case INVALID_NAME -> showToast(MenuLocalization.tr("alt.invalid_name"));
            case IN_GAME -> showToast(MenuLocalization.tr("alt.change_in_game"));
            case FAILED -> showToast(MenuLocalization.tr("alt.change_failed"));
        }
    }

    private void moveAccountToDeleted(int index) {
        AltAccountController.Snapshot snapshot = accountController.snapshot();
        UiStateSnapshot previousUiState = captureUiState();
        AltAccount removedAccount = accountController.moveToDeleted(index);
        if (removedAccount == null) {
            return;
        }

        if (selectedIdx == index) {
            selectedIdx = -1;
        } else if (selectedIdx > index) {
            selectedIdx--;
        }

        clampScroll();
        ensureValidSelection();
        if (!persistAccounts(snapshot, previousUiState)) {
            return;
        }
        showToast(MenuLocalization.tr("alt.deleted_one", removedAccount.name));
    }

    private void togglePinned(int index) {
        String selectedName = getSelectedName();
        AltAccountController.Snapshot snapshot = accountController.snapshot();
        UiStateSnapshot previousUiState = captureUiState();
        AltAccount account = accountController.togglePinned(index, sortMode);
        if (account == null) {
            return;
        }

        selectActiveAccountByName(selectedName == null ? account.name : selectedName);
        ensureValidSelection();
        if (!persistAccounts(snapshot, previousUiState)) {
            return;
        }
    }

    private void restoreDeletedAccount(int deletedIndex) {
        AltAccountController.Snapshot snapshot = accountController.snapshot();
        UiStateSnapshot previousUiState = captureUiState();
        AltAccount restored = accountController.restoreDeleted(deletedIndex, sortMode);
        if (restored == null) {
            return;
        }

        selectActiveAccountByName(restored.name);
        ensureValidSelection();
        if (!persistAccounts(snapshot, previousUiState)) {
            return;
        }
        showToast(MenuLocalization.tr("alt.restored", restored.name));
    }

    private void deleteDeletedPermanently(int deletedIndex) {
        AltAccountController.Snapshot snapshot = accountController.snapshot();
        UiStateSnapshot previousUiState = captureUiState();
        String removed = accountController.deleteDeletedPermanently(deletedIndex);
        if (removed == null) {
            return;
        }

        clampTrashScroll();
        if (!persistAccounts(snapshot, previousUiState)) {
            return;
        }
        showToast(MenuLocalization.tr("alt.deleted_permanent", removed));
    }

    private void clearActiveAccounts() {
        AltAccountController.Snapshot snapshot = accountController.snapshot();
        UiStateSnapshot previousUiState = captureUiState();
        if (!accountController.clearActive()) {
            return;
        }

        selectedIdx = -1;
        scroll = 0;
        if (!persistAccounts(snapshot, previousUiState)) {
            return;
        }
        ensureValidSelection();
        showToast(MenuLocalization.tr("alt.deleted_all_done"));
    }

    private void ensureValidSelection() {
        if (accounts.isEmpty()) {
            selectedIdx = -1;
        } else if (selectedIdx < 0 || selectedIdx >= accounts.size()) {
            selectedIdx = 0;
        }
        scrollSelectionIntoView();
        clampTrashScroll();
        clampTrashSelection();
    }

    private void loadAccounts() {
        sortMode = accountController.load(sortMode);
        if (accountController.selectedActiveName() == null) {
            String preferredName = AltStartupSessionSync.getPreferredStoredName();
            if (preferredName != null) {
                AltAccountController.Snapshot snapshot = accountController.snapshot();
                accountController.setSelectedActiveName(preferredName);
                if (!saveAccounts()) {
                    accountController.restore(snapshot);
                }
            }
        }
        syncSelectionToCurrentSession();
        ensureValidSelection();
    }

    private boolean saveAccounts() {
        if (!accountController.save(sortMode)) {
            showToast(MenuLocalization.tr("alt.save_failed"));
            return false;
        }

        AltStartupSessionSync.refresh();
        return true;
    }

    private UiStateSnapshot captureUiState() {
        return new UiStateSnapshot(selectedIdx, scroll, scrollSmooth, trashScroll, trashSelectedIdx);
    }

    private void restoreUiState(UiStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        selectedIdx = snapshot.selectedIdx();
        scroll = snapshot.scroll();
        scrollSmooth = snapshot.scrollSmooth();
        trashScroll = snapshot.trashScroll();
        trashSelectedIdx = snapshot.trashSelectedIdx();
    }

    private boolean persistAccounts(AltAccountController.Snapshot snapshot, UiStateSnapshot previousUiState) {
        accountController.setSelectedActiveName(getSelectedName());
        if (saveAccounts()) {
            return true;
        }

        accountController.restore(snapshot);
        restoreUiState(previousUiState);
        ensureValidSelection();
        return false;
    }

    private void showToast(String message) {
        toast = message;
        toastTick = TOAST_TICKS;
    }

    private void pasteAltNameFromClipboard() {
        if (client == null || client.keyboard == null) {
            return;
        }

        String clipboard = client.keyboard.getClipboard();
        String filtered = filterAltNameCharacters(clipboard);
        if (filtered.isEmpty()) {
            showToast(MenuLocalization.tr("alt.invalid_name_hint"));
            return;
        }

        int previousLength = inputText.length();
        appendAltNameCharacters(filtered);
        if (inputText.length() == previousLength) {
            showToast(MenuLocalization.tr("alt.invalid_name_hint"));
        }
    }

    private boolean handleFocusedInputKeyPressed(int keyCode) {
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            pasteAltNameFromClipboard();
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            writeClipboardText(inputText);
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_X) {
            writeClipboardText(inputText);
            inputText = "";
            cursorBlink = 0;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            addAccount();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            blurInputField();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasControlDown()) {
                inputText = "";
            } else if (!inputText.isEmpty()) {
                inputText = inputText.substring(0, inputText.length() - 1);
            }
            cursorBlink = 0;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            inputText = "";
            cursorBlink = 0;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END
                || keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            return true;
        }

        return true;
    }

    private boolean handleSelectionNavigationKey(int keyCode) {
        if (accounts.isEmpty()) {
            return false;
        }

        int currentIndex = selectedIdx;
        if (currentIndex < 0 || currentIndex >= accounts.size()) {
            currentIndex = 0;
        }

        int nextIndex = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> Math.max(0, currentIndex - 1);
            case GLFW.GLFW_KEY_RIGHT -> Math.min(accounts.size() - 1, currentIndex + 1);
            case GLFW.GLFW_KEY_UP -> Math.max(0, currentIndex - COLS);
            case GLFW.GLFW_KEY_DOWN -> Math.min(accounts.size() - 1, currentIndex + COLS);
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> accounts.size() - 1;
            case GLFW.GLFW_KEY_PAGE_UP -> Math.max(0, currentIndex - VISIBLE_ROWS * COLS);
            case GLFW.GLFW_KEY_PAGE_DOWN -> Math.min(accounts.size() - 1, currentIndex + VISIBLE_ROWS * COLS);
            default -> -1;
        };

        if (nextIndex == -1 || nextIndex == currentIndex && selectedIdx == currentIndex) {
            return nextIndex != -1;
        }

        selectedIdx = nextIndex;
        scrollSelectionIntoView();
        return true;
    }

    private boolean handleGlobalShortcutKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_INSERT || keyCode == GLFW.GLFW_KEY_TAB || (hasControlDown() && keyCode == GLFW.GLFW_KEY_N)) {
            focusInputField();
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_R) {
            blurInputField();
            randomNick();
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_S) {
            if (sortDropdownOpen) {
                closeSortDropdown();
            } else {
                openSortDropdown();
            }
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_T) {
            toggleTrashPanel();
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_P && selectedIdx >= 0 && selectedIdx < accounts.size()) {
            togglePinned(selectedIdx);
            return true;
        }

        return false;
    }

    private void appendAltNameCharacters(String value) {
        if (value == null || value.isEmpty() || inputText.length() >= MAX_ALT_NAME_LENGTH) {
            return;
        }

        String filtered = filterAltNameCharacters(value);
        if (filtered.isEmpty()) {
            return;
        }

        int remaining = MAX_ALT_NAME_LENGTH - inputText.length();
        inputText += filtered.length() <= remaining ? filtered : filtered.substring(0, remaining);
        cursorBlink = 0;
    }

    private static String filterAltNameCharacters(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (isNickChar(character)) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private String getCurrentName() {
        return sessionService.getCurrentName(client);
    }

    private void closeToParent() {
        if (client != null) {
            Screen target = parent != null ? parent : new StarterMenuScreen(new net.minecraft.client.gui.screen.TitleScreen());
            client.setScreen(target);
        }
    }

    private void openSortDropdown() {
        blurInputField();
        sortDropdownOpen = true;
        sortDropdownIndex = sortMode.ordinal();
    }

    private void closeSortDropdown() {
        sortDropdownOpen = false;
        sortDropdownIndex = -1;
    }

    private void toggleTrashPanel() {
        blurInputField();
        if (trashOpen) {
            closeTrashPanel();
            return;
        }

        trashOpen = true;
        clampTrashSelection();
        ensureTrashSelectionVisible();
    }

    private void closeTrashPanel() {
        trashOpen = false;
        trashSelectedIdx = -1;
    }

    private void writeClipboardText(String value) {
        if (client == null || client.keyboard == null) {
            return;
        }

        client.keyboard.setClipboard(value == null ? "" : value);
    }

    private static int argbToAbgr(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private static int imgColor(int color, float alphaMultiplier) {
        return argbToAbgr(mixAlpha(color, alphaMultiplier));
    }

    private void clampTrashScroll() {
        int maxScroll = Math.max(0, deletedAccounts.size() - TRASH_VISIBLE);
        trashScroll = Math.max(0, Math.min(trashScroll, maxScroll));
    }

    private void clampTrashSelection() {
        if (deletedAccounts.isEmpty()) {
            trashSelectedIdx = -1;
            return;
        }

        if (trashSelectedIdx < 0 || trashSelectedIdx >= deletedAccounts.size()) {
            trashSelectedIdx = Math.min(trashScroll, deletedAccounts.size() - 1);
        }
    }

    private void ensureTrashSelectionVisible() {
        clampTrashSelection();
        if (trashSelectedIdx < 0) {
            return;
        }

        if (trashSelectedIdx < trashScroll) {
            trashScroll = trashSelectedIdx;
        } else if (trashSelectedIdx >= trashScroll + TRASH_VISIBLE) {
            trashScroll = trashSelectedIdx - TRASH_VISIBLE + 1;
        }
        clampTrashScroll();
    }

    private void snapTrashSelectionToVisibleWindow() {
        clampTrashScroll();
        clampTrashSelection();
        if (trashSelectedIdx < 0) {
            return;
        }

        if (trashSelectedIdx < trashScroll) {
            trashSelectedIdx = trashScroll;
        } else if (trashSelectedIdx >= trashScroll + TRASH_VISIBLE) {
            trashSelectedIdx = Math.min(deletedAccounts.size() - 1, trashScroll + TRASH_VISIBLE - 1);
        }
    }

    private void selectActiveAccountByName(String name) {
        selectedIdx = accountController.findActiveIndex(name);
    }

    private void scrollSelectionIntoView() {
        if (selectedIdx < 0 || accounts.isEmpty()) {
            clampScroll();
            return;
        }

        int selectedRow = selectedIdx / COLS;
        if (selectedRow < scroll) {
            scroll = selectedRow;
        } else if (selectedRow >= scroll + VISIBLE_ROWS) {
            scroll = selectedRow - VISIBLE_ROWS + 1;
        }
        clampScroll();
    }

    private void syncSelectionToCurrentSession() {
        String preferredName = accountController.selectedActiveName();
        int preferredIndex = accountController.findActiveIndex(preferredName);
        if (preferredIndex != -1) {
            selectedIdx = preferredIndex;
        }

        String currentName = sessionService.getCurrentName(client);
        int liveIndex = accountController.findActiveIndex(currentName);
        if (liveIndex != -1) {
            selectedIdx = liveIndex;
        }
    }

    private boolean handleSortDropdownKeyPressed(int keyCode) {
        AltSortMode[] modes = AltSortMode.values();
        int directIndex = keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + modes.length
                ? keyCode - GLFW.GLFW_KEY_1
                : keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode < GLFW.GLFW_KEY_KP_1 + modes.length
                ? keyCode - GLFW.GLFW_KEY_KP_1
                : -1;
        if (directIndex != -1) {
            sortDropdownIndex = directIndex;
            closeSortDropdown();
            applySortMode(modes[directIndex]);
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> sortDropdownIndex = Math.max(0, sortDropdownIndex - 1);
            case GLFW.GLFW_KEY_DOWN -> sortDropdownIndex = Math.min(modes.length - 1, Math.max(0, sortDropdownIndex + 1));
            case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_PAGE_UP -> sortDropdownIndex = 0;
            case GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_PAGE_DOWN -> sortDropdownIndex = modes.length - 1;
            case GLFW.GLFW_KEY_TAB -> closeSortDropdown();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                int resolvedIndex = sortDropdownIndex < 0 ? sortMode.ordinal() : sortDropdownIndex;
                closeSortDropdown();
                applySortMode(modes[resolvedIndex]);
            }
            default -> {
                return true;
            }
        }
        return true;
    }

    private boolean handleTrashPanelKeyPressed(int keyCode) {
        if (deletedAccounts.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                closeTrashPanel();
            }
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> trashSelectedIdx = Math.max(0, trashSelectedIdx - 1);
            case GLFW.GLFW_KEY_DOWN -> trashSelectedIdx = Math.min(deletedAccounts.size() - 1, trashSelectedIdx + 1);
            case GLFW.GLFW_KEY_HOME -> trashSelectedIdx = 0;
            case GLFW.GLFW_KEY_END -> trashSelectedIdx = deletedAccounts.size() - 1;
            case GLFW.GLFW_KEY_PAGE_UP -> trashSelectedIdx = Math.max(0, trashSelectedIdx - TRASH_VISIBLE);
            case GLFW.GLFW_KEY_PAGE_DOWN -> trashSelectedIdx = Math.min(deletedAccounts.size() - 1, trashSelectedIdx + TRASH_VISIBLE);
            case GLFW.GLFW_KEY_TAB -> {
                closeTrashPanel();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                restoreDeletedAccount(trashSelectedIdx);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                deleteDeletedPermanently(trashSelectedIdx);
                return true;
            }
            default -> {
                return true;
            }
        }

        ensureTrashSelectionVisible();
        return true;
    }
}
