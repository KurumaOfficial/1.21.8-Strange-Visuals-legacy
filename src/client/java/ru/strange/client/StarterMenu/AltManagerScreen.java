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
    private static final int SORT_ITEM_H = 14;

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
        return px + PANEL_W - 148;
    }

    private int trashPanelY() {
        int itemCount = Math.max(1, Math.min(TRASH_VISIBLE, deletedAccounts.size()));
        return bottomY() - (26 + itemCount * 20) - 6;
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

        RenderUtil.Shadow.draw(context, px - 3, py - 3, PANEL_W, PANEL_H, PANEL_R + 2, 18, mixAlpha(rgba(0, 0, 0, 112), drawAlpha));
        RenderUtil.Round.draw(context, px, py, PANEL_W, PANEL_H, PANEL_R, mixAlpha(panelColor, drawAlpha));
        RenderUtil.Border.draw(context, px, py, PANEL_W, PANEL_H, PANEL_R, 0.45f, mixAlpha(line, drawAlpha));
        RenderUtil.Round.draw(context, px + 10, py + 8, 74, 4, 2.5f, mixAlpha(accentSoft, drawAlpha));
        RenderUtil.Round.draw(context, px + 10, py + 8, 28, 4, 2.5f, mixAlpha(accent, drawAlpha));

        int dividerY = py + HEADER_H;
        context.fill(px + 6, dividerY, px + PANEL_W - 6, dividerY + 1, mixAlpha(alphaColor(text, 10), drawAlpha));

        String sortLabel = currentSortLabel();
        int sortW = Math.max(64, (int) textWidth(sortLabel, 5) + 20);
        int sortH = 16;
        int sortX = px + PANEL_W - sortW - 6;
        int sortY = py + 5;
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
            int dropdownX = sortX;
            int dropdownY = sortY + sortH + 4;
            int dropdownH = modes.length * SORT_ITEM_H + 4;

            RenderUtil.Shadow.draw(context, dropdownX - 2, dropdownY - 2, sortW, dropdownH, 7, 12, mixAlpha(rgba(0, 0, 0, 96), drawAlpha));
            RenderUtil.Round.draw(context, dropdownX, dropdownY, sortW, dropdownH, 5, mixAlpha(sectionColor, drawAlpha));
            RenderUtil.Border.draw(context, dropdownX, dropdownY, sortW, dropdownH, 5, 0.45f, mixAlpha(line, drawAlpha));

            for (int i = 0; i < modes.length; i++) {
                int itemY = dropdownY + 2 + i * SORT_ITEM_H;
                boolean hovered = inRect(mouseX, mouseY, dropdownX + 2, itemY, sortW - 4, SORT_ITEM_H);
                if (hovered || modes[i] == sortMode) {
                    RenderUtil.Round.draw(context, dropdownX + 2, itemY, sortW - 4, SORT_ITEM_H, 3,
                            mixAlpha(alphaColor(mixColor(elevatedColor, accent, modes[i] == sortMode ? 0.22f : 0.14f),
                                    modes[i] == sortMode ? 238 : 220), drawAlpha));
                }
            }
        }

        if (trashOpen) {
            int panelX = trashPanelX();
            int panelY = trashPanelY();
            int panelW = 148;
            int visibleCount = Math.min(TRASH_VISIBLE, deletedAccounts.size());
            int itemCount = Math.max(1, visibleCount);
            int panelH = 26 + itemCount * 20;

            RenderUtil.Shadow.draw(context, panelX - 2, panelY - 2, panelW, panelH, 8, 12, mixAlpha(rgba(0, 0, 0, 96), drawAlpha));
            RenderUtil.Round.draw(context, panelX, panelY, panelW, panelH, 7, mixAlpha(sectionColor, drawAlpha));
            RenderUtil.Border.draw(context, panelX, panelY, panelW, panelH, 7, 0.45f, mixAlpha(line, drawAlpha));
            context.fill(panelX + 8, panelY + 22, panelX + panelW - 8, panelY + 23, mixAlpha(alphaColor(text, 8), drawAlpha));

            boolean closeHover = inRect(mouseX, mouseY, panelX + panelW - 16, panelY + 5, 10, 10);
            RenderUtil.Image.draw(context, CLOSE_TEX, panelX + panelW - 13, panelY + 8, 6, 6,
                    imgColor(alphaColor(closeHover ? text : muted, closeHover ? 240 : 190), drawAlpha));

            for (int i = 0; i < visibleCount; i++) {
                int deletedIndex = trashScroll + i;
                if (deletedIndex >= deletedAccounts.size()) {
                    break;
                }
                int itemY = panelY + 26 + i * 20;
                boolean hovered = inRect(mouseX, mouseY, panelX + 5, itemY, panelW - 10, 16);
                RenderUtil.Round.draw(context, panelX + 5, itemY, panelW - 10, 16, 5,
                        mixAlpha(alphaColor(mixColor(elevatedColor, accent, hovered ? 0.18f : 0.08f), hovered ? 236 : 220), drawAlpha));
                RenderUtil.Image.draw(context, PERSON_TEX, panelX + 9, itemY + 2, 12, 12,
                        imgColor(alphaColor(text, 216), drawAlpha));

                boolean arrowHover = inRect(mouseX, mouseY, panelX + panelW - 21, itemY + 2, 12, 12);
                RenderUtil.Image.draw(context, ARROW_TEX, panelX + panelW - 20, itemY + 4, 7, 7,
                        imgColor(alphaColor(arrowHover ? accent : muted, arrowHover ? 240 : 210), drawAlpha));

                boolean deleteHover = inRect(mouseX, mouseY, panelX + panelW - 34, itemY + 2, 12, 12);
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

        RenderUtil.Shadow.draw(context, deleteX - 2, groupY - 2, deleteW, groupH, 8, 10, mixAlpha(rgba(0, 0, 0, 76), drawAlpha));
        RenderUtil.Round.draw(context, deleteX, groupY, deleteW, groupH, 7,
                mixAlpha(themeDangerSurfaceColor(deleteHover), drawAlpha));
        RenderUtil.Border.draw(context, deleteX, groupY, deleteW, groupH, 7, 0.45f,
                mixAlpha(deleteHover ? alphaColor(themeDangerAccentColor(), 124) : line, drawAlpha));

        int trashX = trashTabX();
        int trashW = trashButtonWidth();
        boolean trashHover = inRect(mouseX, mouseY, trashX, groupY, trashW, groupH);
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
        int sortW = Math.max(64, (int) textWidth(sortLabel, 5) + 20);
        int sortX = px + PANEL_W - sortW - 6;
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
            int dropdownX = sortX;
            int dropdownY = py + 5 + 16 + 4;
            for (int i = 0; i < modes.length; i++) {
                int itemY = dropdownY + 2 + i * SORT_ITEM_H;
                int color = modes[i] == sortMode ? mixAlpha(currentColor, drawAlpha) : mixAlpha(text, drawAlpha);
                drawCenteredMidText(context, modes[i].label(), dropdownX + sortW / 2.0f, itemY + SORT_ITEM_H / 2.0f, 4, color);
            }
        }

        if (trashOpen) {
            int panelX = trashPanelX();
            int panelY = trashPanelY();
            int panelW = 148;
            drawCenteredMidText(context, MenuLocalization.tr("alt.deleted_profiles"), panelX + panelW / 2.0f, panelY + 11, 5, mixAlpha(text, drawAlpha));

            if (deletedAccounts.isEmpty()) {
                drawCenteredMidText(context, MenuLocalization.tr("alt.deleted_empty"), panelX + panelW / 2.0f, panelY + 36, 4, mixAlpha(placeholder, drawAlpha));
            } else {
                int visibleCount = Math.min(TRASH_VISIBLE, deletedAccounts.size());
                for (int i = 0; i < visibleCount; i++) {
                    int deletedIndex = trashScroll + i;
                    if (deletedIndex >= deletedAccounts.size()) {
                        break;
                    }
                    int itemY = panelY + 26 + i * 20;
                    String name = trimToWidth(deletedAccounts.get(deletedIndex).name, panelW - 42, 4);
                    drawMidText(context, name, panelX + 23, itemY + 8, 4, mixAlpha(muted, drawAlpha));
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

        String sortLabel = currentSortLabel();
        int sortW = Math.max(64, (int) textWidth(sortLabel, 5) + 20);
        int sortH = 16;
        int sortX = px + PANEL_W - sortW - 6;
        int sortY = py + 5;

        if (sortDropdownOpen) {
            AltSortMode[] modes = AltSortMode.values();
            int dropdownX = sortX;
            int dropdownY = sortY + sortH + 4;
            for (int i = 0; i < modes.length; i++) {
                int itemY = dropdownY + 2 + i * SORT_ITEM_H;
                if (inRect(mouseX, mouseY, dropdownX + 2, itemY, sortW - 4, SORT_ITEM_H)) {
                    sortMode = modes[i];
                    sortDropdownOpen = false;
                    sortAccounts();
                    return true;
                }
            }

            if (inRect(mouseX, mouseY, sortX, sortY, sortW, sortH)) {
                sortDropdownOpen = false;
                return true;
            }

            sortDropdownOpen = false;
            return true;
        }

        if (inRect(mouseX, mouseY, sortX, sortY, sortW, sortH)) {
            sortDropdownOpen = true;
            return true;
        }

        if (inRect(mouseX, mouseY, trashTabX(), trashTabY(), trashButtonWidth(), BOTTOM_H)) {
            trashOpen = !trashOpen;
            trashScroll = 0;
            return true;
        }

        if (trashOpen) {
            int panelX = trashPanelX();
            int panelY = trashPanelY();
            int panelW = 148;
            if (inRect(mouseX, mouseY, panelX + panelW - 16, panelY + 5, 10, 10)) {
                trashOpen = false;
                return true;
            }

            int visibleCount = Math.min(TRASH_VISIBLE, deletedAccounts.size());
            for (int i = 0; i < visibleCount; i++) {
                int deletedIndex = trashScroll + i;
                if (deletedIndex >= deletedAccounts.size()) {
                    break;
                }
                int itemY = panelY + 26 + i * 20;

                if (inRect(mouseX, mouseY, panelX + panelW - 34, itemY + 2, 12, 12)) {
                    deleteDeletedPermanently(deletedIndex);
                    return true;
                }

                if (inRect(mouseX, mouseY, panelX + 5, itemY, panelW - 10, 16)) {
                    restoreDeletedAccount(deletedIndex);
                    return true;
                }
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
            inputFocused = true;
            return true;
        }

        if (inRect(mouseX, mouseY, separator1, groupY, RANDOM_SEG_W, groupH)) {
            inputFocused = false;
            randomNick();
            return true;
        }

        if (inRect(mouseX, mouseY, separator2, groupY, groupW - (separator2 - groupX), groupH)) {
            inputFocused = false;
            addAccount();
            return true;
        }

        if (inRect(mouseX, mouseY, deleteX, groupY, deleteW, groupH)) {
            inputFocused = false;
            clearActiveAccounts();
            return true;
        }

        int listX = px + GRID_PAD_L;
        int listY = py + HEADER_H + 5;
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

                inputFocused = false;

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

        inputFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (trashOpen) {
            int panelX = trashPanelX();
            int panelY = trashPanelY();
            int panelW = 148;
            int visibleCount = Math.min(TRASH_VISIBLE, deletedAccounts.size());
            int panelH = 26 + Math.max(1, visibleCount) * 20;
            if (inRect(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
                int maxScroll = Math.max(0, deletedAccounts.size() - TRASH_VISIBLE);
                trashScroll = Math.max(0, Math.min(trashScroll - (int) Math.signum(verticalAmount), maxScroll));
                return true;
            }
        }

        int totalRows = (accounts.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(verticalAmount), maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                addAccount();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!inputText.isEmpty()) {
                    inputText = inputText.substring(0, inputText.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                inputFocused = false;
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (sortDropdownOpen) {
                sortDropdownOpen = false;
                return true;
            }
            if (trashOpen) {
                trashOpen = false;
                return true;
            }
            closeToParent();
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
            if (inputText.length() >= 16) {
                return true;
            }
            if (isNickChar(chr)) {
                inputText += chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
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

        AltAccount account = accountController.createAccount(name, sortMode);
        selectActiveAccountByName(account.name);
        saveAccounts();
        inputText = "";
        clampScroll();
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

        AltAccount account = accountController.createAccount(nick, sortMode);
        selectActiveAccountByName(account.name);
        saveAccounts();
        scrollToLastRows();
        showToast(MenuLocalization.tr("alt.generated", account.name));
    }

    private void sortAccounts() {
        String selectedName = getSelectedName();
        accountController.sortActive(sortMode);
        selectedIdx = accountController.findActiveIndex(selectedName);
        ensureValidSelection();
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

        accountController.setSelectedActiveName(account.name);
        saveAccounts();
        AltStartupSessionSync.refresh();

        AltSessionService.SwitchResult result = sessionService.switchToOfflineProfile(client, account.name);
        switch (result) {
            case SUCCESS -> {
                syncSelectionToCurrentSession();
                showToast(MenuLocalization.tr("alt.changed", account.name));
            }
            case INVALID_NAME -> showToast(MenuLocalization.tr("alt.invalid_name"));
            case IN_GAME -> showToast(MenuLocalization.tr("alt.change_in_game"));
            case FAILED -> showToast(MenuLocalization.tr("alt.change_failed"));
        }
    }

    private void moveAccountToDeleted(int index) {
        AltAccount removedAccount = accountController.moveToDeleted(index);
        if (removedAccount == null) {
            return;
        }

        if (selectedIdx == index) {
            selectedIdx = -1;
        } else if (selectedIdx > index) {
            selectedIdx--;
        }

        saveAccounts();
        clampScroll();
        ensureValidSelection();
        showToast(MenuLocalization.tr("alt.deleted_one", removedAccount.name));
    }

    private void togglePinned(int index) {
        String selectedName = getSelectedName();
        AltAccount account = accountController.togglePinned(index, sortMode);
        if (account == null) {
            return;
        }

        saveAccounts();
        selectActiveAccountByName(selectedName == null ? account.name : selectedName);
        ensureValidSelection();
    }

    private void restoreDeletedAccount(int deletedIndex) {
        AltAccount restored = accountController.restoreDeleted(deletedIndex, sortMode);
        if (restored == null) {
            return;
        }

        selectActiveAccountByName(restored.name);
        clampTrashScroll();
        saveAccounts();
        showToast(MenuLocalization.tr("alt.restored", restored.name));
    }

    private void deleteDeletedPermanently(int deletedIndex) {
        String removed = accountController.deleteDeletedPermanently(deletedIndex);
        if (removed == null) {
            return;
        }

        clampTrashScroll();
        saveAccounts();
        showToast(MenuLocalization.tr("alt.deleted_permanent", removed));
    }

    private void clearActiveAccounts() {
        if (!accountController.clearActive()) {
            return;
        }

        selectedIdx = -1;
        scroll = 0;
        saveAccounts();
        ensureValidSelection();
        showToast(MenuLocalization.tr("alt.deleted_all_done"));
    }

    private void ensureValidSelection() {
        if (accounts.isEmpty()) {
            selectedIdx = -1;
        } else if (selectedIdx < 0 || selectedIdx >= accounts.size()) {
            selectedIdx = 0;
        }
        clampScroll();
        clampTrashScroll();
    }

    private void loadAccounts() {
        accountController.load(sortMode);
        if (accountController.selectedActiveName() == null) {
            String preferredName = AltStartupSessionSync.getPreferredStoredName();
            if (preferredName != null) {
                accountController.setSelectedActiveName(preferredName);
                saveAccounts();
                AltStartupSessionSync.refresh();
            }
        }
        syncSelectionToCurrentSession();
        ensureValidSelection();
    }

    private void saveAccounts() {
        if (!accountController.save()) {
            showToast(MenuLocalization.tr("alt.save_failed"));
        }
    }

    private void showToast(String message) {
        toast = message;
        toastTick = TOAST_TICKS;
    }

    private String getCurrentName() {
        return sessionService.getCurrentName(client);
    }

    private void closeToParent() {
        if (client != null) {
            client.setScreen(parent);
        }
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

    private void scrollToLastRows() {
        int totalRows = (accounts.size() + COLS - 1) / COLS;
        scroll = Math.max(0, totalRows - VISIBLE_ROWS);
    }

    private void clampTrashScroll() {
        int maxScroll = Math.max(0, deletedAccounts.size() - TRASH_VISIBLE);
        trashScroll = Math.max(0, Math.min(trashScroll, maxScroll));
    }

    private void selectActiveAccountByName(String name) {
        selectedIdx = accountController.findActiveIndex(name);
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
}
