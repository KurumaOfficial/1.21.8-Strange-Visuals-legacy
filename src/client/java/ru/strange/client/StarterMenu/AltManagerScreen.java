package ru.strange.client.StarterMenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.session.Session;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.mixin.MinecraftClientAccessor;
import ru.strange.client.utils.io.AtomicFileIO;
import ru.strange.client.utils.other.ServerUtil;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class AltManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 24;

    // как в примере: кнопки крупнее
    private static final int BTN_H = 20;
    private static final int BTN_W = 150;

    private static final int GAP = 6;
    private static final int SIDE_PADDING = 30;

    // правая панель как в примере (уже/ванильнее)
    private static final int INFO_PANEL_WIDTH = 140;

    // левая зона под полный скин
    private static final int PREVIEW_WIDTH = 120;

    private static final File ALTS_FILE = new File(Strange.root, "alts.json");

    // панели (чтобы не было “всё чёрное”)
    private static final int PANEL_BG = 0xB0141414;
    private static final int PANEL_INNER = 0x80101010;
    private static final int PANEL_BORDER = 0x55FFFFFF;

    // кеш скинов по нику
    private final Map<String, SkinTextures> skinCache = new HashMap<>();

    private final Screen parent;
    private final AltAccountController accountController = new AltAccountController(new AltAccountStore(ALTS_FILE));
    private final AltSessionService sessionService = new AltSessionService();
    private final List<AltAccount> activeAccounts = accountController.activeAccounts();
    private final List<AltAccount> deletedAccounts = accountController.deletedAccounts();

    private TextFieldWidget searchField;
    private AltListWidget listWidget;

    private ButtonWidget loginButton, toggleButton, addButton;
    private ButtonWidget switchButton, deleteButton, doneButton;

    private AltSortMode sortMode = AltSortMode.NEWEST;
    private boolean showingDeleted;
    private int selectedActiveIndex = -1;
    private int selectedDeletedIndex = -1;
    private String searchText = "";
    private List<AltAccount> filteredAccounts = new ArrayList<>();

    private record UiStateSnapshot(boolean showingDeleted, int selectedActiveIndex,
                                   int selectedDeletedIndex, String searchText) {}

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
        loadAccounts();
    }

    @Override
    protected void init() {
        super.init();

        int listWidth = getListWidth();
        int listLeft = getListLeft();
        int listTop = getListTop();
        int listBottom = getListBottom();

        // Search (важно: сначала без listener, чтобы не было NPE на кнопках)
        searchField = new TextFieldWidget(textRenderer, listLeft, listTop - 22, listWidth, 20, Text.empty());
        searchField.setMaxLength(32);
        searchField.setPlaceholder(Text.literal("Search"));
        searchField.setText(searchText);
        addDrawableChild(searchField);

        listWidget = new AltListWidget(client, listLeft, listWidth, listTop, listBottom, ROW_HEIGHT);
        addSelectableChild(listWidget);

        // Buttons: 2 rows of 3 (как в примере)
        int rowW = BTN_W * 3 + GAP * 2;
        int row1X = width / 2 - rowW / 2;
        int row1Y = height - SIDE_PADDING - BTN_H * 2 - GAP;
        int row2Y = height - SIDE_PADDING - BTN_H;

        loginButton = addDrawableChild(btn(row1X, row1Y, BTN_W, b -> handlePrimaryAction()));
        toggleButton = addDrawableChild(btn(row1X + BTN_W + GAP, row1Y, BTN_W, b -> toggleMode()));
        addButton = addDrawableChild(btn(row1X + (BTN_W + GAP) * 2, row1Y, BTN_W, b -> addAccount()));

        // нижний ряд: “offline” (у тебя это просто второе действие), delete, cancel
        switchButton = addDrawableChild(btn(row1X, row2Y, BTN_W, b -> useSelectedProfile()));
        deleteButton = addDrawableChild(btn(row1X + BTN_W + GAP, row2Y, BTN_W, b -> handleDeleteAction()));
        doneButton = addDrawableChild(btn(row1X + (BTN_W + GAP) * 2, row2Y, BTN_W, b -> close()));

        // listener после кнопок
        searchField.setChangedListener(v -> {
            searchText = v;
            rebuildFiltered();
            updateWidgets();
        });

        clampState();
        rebuildFiltered();
        updateWidgets();
        setInitialFocus(searchField);
    }

    private ButtonWidget btn(int x, int y, int w, ButtonWidget.PressAction a) {
        return ButtonWidget.builder(Text.empty(), a).dimensions(x, y, w, BTN_H).build();
    }

    // ===== LAYOUT =====

    private int getListWidth() {
        int max = 340;
        int available = width - SIDE_PADDING * 2 - PREVIEW_WIDTH - INFO_PANEL_WIDTH - GAP * 2;
        return Math.max(220, Math.min(max, available));
    }

    private int getContentLeft() {
        int contentWidth = PREVIEW_WIDTH + GAP + getListWidth() + GAP + INFO_PANEL_WIDTH;
        return width / 2 - contentWidth / 2;
    }

    private int getPreviewLeft() {
        return getContentLeft();
    }

    private int getListLeft() {
        return getContentLeft() + PREVIEW_WIDTH + GAP;
    }

    private int getInfoLeft() {
        return getListLeft() + getListWidth() + GAP;
    }

    private int getListTop() {
        return SIDE_PADDING + 40;
    }

    private int getListBottom() {
        return height - SIDE_PADDING - (BTN_H * 2 + GAP + 10);
    }

    @Override
    public void resize(MinecraftClient client, int w, int h) {
        String saved = searchField != null ? searchField.getText() : searchText;
        super.resize(client, w, h);
        searchText = saved;
        if (searchField != null) searchField.setText(saved);
        rebuildFiltered();
        updateWidgets();
    }

    // ===== RENDER (как на примере) =====

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);

        int listLeft = getListLeft();
        int listWidth = getListWidth();
        int listTop = getListTop();
        int listBottom = getListBottom();

        int previewLeft = getPreviewLeft();
        int previewRight = previewLeft + PREVIEW_WIDTH;

        int infoLeft = getInfoLeft();
        int infoRight = infoLeft + INFO_PANEL_WIDTH;

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Select an account"), width / 2, 15, 0xFFFFFF);

        // панели
        drawPanel(ctx, previewLeft - 1, listTop - 1, previewRight + 1, listBottom + 1);
        drawPanel(ctx, listLeft - 1, listTop - 1, listLeft + listWidth + 1, listBottom + 1);
        drawPanel(ctx, infoLeft - 1, listTop - 1, infoRight + 1, listBottom + 1);

        // список
        if (listWidget != null) {
            listWidget.render(ctx, mouseX, mouseY, delta);
        }

        // если пусто
        if (filteredAccounts.isEmpty()) {
            String empty = "Empty";
            int tx = listLeft + listWidth / 2 - textRenderer.getWidth(empty) / 2;
            int ty = (listTop + listBottom) / 2 - 4;
            ctx.drawTextWithShadow(textRenderer, Text.literal(empty), tx, ty, 0xA0A0A0);
        }

        // левая панель: полный “3D” (как на примере — полный персонаж из скина)
        AltAccount sel = selectedAccount();
        if (sel != null) {
            int scale = 4;            // можно 5 если хочешь крупнее
            int modelW = 16 * scale;  // 4+8+4
            int modelH = 32 * scale;  // 8+12+12

            int dx = previewLeft + (PREVIEW_WIDTH - modelW) / 2;
            int dy = listTop + 18;

            // чтобы не вылезало за нижнюю панель
            int maxDy = listBottom - modelH - 10;
            if (dy > maxDy) dy = maxDy;

            drawFullSkin(ctx, sel.name, dx, dy, scale);
        }

        // правая панель: как в примере, без дат
        int y = listTop + 10;
        if (sel == null) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("No account"), infoLeft + 10, y, 0xA0A0A0);
        } else {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Premium"), infoLeft + 10, y, 0x55FF55);
            y += 14;

            ctx.drawTextWithShadow(textRenderer, Text.literal("Times Used:"), infoLeft + 10, y, 0xFFFFFF);
            y += 12;

            ctx.drawTextWithShadow(textRenderer, Text.literal(String.valueOf(sel.timesUsed)), infoLeft + 10, y, 0xFFFFFF);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ===== INPUT =====

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            toggleMode();
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
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!showingDeleted
                    && searchField != null
                    && searchField.isFocused()
                    && !searchField.getText().isBlank()
                    && filteredAccounts.isEmpty()) {
                addAccountByName(searchText);
            } else {
                handlePrimaryAction();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client == null) return;
        Screen target = parent != null ? parent : new TitleScreen();
        if (target instanceof TitleScreen) StrangeVisualsClient.suppressTitleScreenReplacement(target);
        client.setScreen(target);
    }

    // ===== ACTIONS =====

    private void toggleMode() {
        showingDeleted = !showingDeleted;
        clampState();
        rebuildFiltered();
        updateWidgets();
    }

    private void handlePrimaryAction() {
        if (showingDeleted) {
            restoreSelectedDeleted();
            return;
        }
        useSelectedProfile();
    }

    private void handleDeleteAction() {
        if (showingDeleted) {
            deleteSelectedDeleted();
            return;
        }
        moveSelectedActiveToDeleted();
    }

    private void moveSelection(int dir) {
        if (filteredAccounts.isEmpty()) return;

        AltAccount current = selectedAccount();
        int i = current == null ? -1 : filteredAccounts.indexOf(current);

        int next = (i < 0) ? 0 : Math.max(0, Math.min(filteredAccounts.size() - 1, i + dir));
        AltAccount target = filteredAccounts.get(next);

        selectAccount(target);
        ensureSelectionVisible();
        updateWidgets();
    }

    private void addAccount() {
        String name = searchText.isBlank() ? NickGenerator.generate() : searchText;
        addAccountByName(name);
    }

    private void addAccountByName(String raw) {
        String name = sessionService.normalizeName(raw);
        if (name == null) {
            showToast("Некорректный ник");
            return;
        }
        if (accountController.containsName(name)) {
            showToast("Уже существует");
            return;
        }

        AltAccountController.Snapshot snap = accountController.snapshot();
        UiStateSnapshot ui = captureUiState();

        AltAccount account = accountController.createAccount(name, sortMode);
        showingDeleted = false;
        selectedActiveIndex = accountController.findActiveIndex(account.name);
        if (!persistAccounts(snap, ui)) return;

        searchText = "";
        if (searchField != null) searchField.setText("");
        rebuildFiltered();
        ensureSelectionVisible();
        updateWidgets();
        showToast("+ " + account.name);
    }

    private void useSelectedProfile() {
        AltAccount account = selectedAccount();
        if (account == null) return;

        AltAccountController.Snapshot snap = accountController.snapshot();
        AltSessionService.SessionSnapshot prev = sessionService.captureSession(client);
        UiStateSnapshot ui = captureUiState();

        AltSessionService.SwitchResult result = sessionService.isCurrentProfile(client, account.name)
                ? AltSessionService.SwitchResult.SUCCESS
                : sessionService.switchToOfflineProfile(client, account.name);

        switch (result) {
            case SUCCESS -> {
                account.markUsed();
                accountController.setSelectedActiveName(account.name);
                if (!persistAccounts(snap, ui)) {
                    sessionService.restoreSession(client, prev);
                    syncSelectionToCurrentSession();
                    return;
                }
                syncSelectionToCurrentSession();
                rebuildFiltered();
                ensureSelectionVisible();
                updateWidgets();
                showToast("-> " + account.name);
            }
            case INVALID_NAME -> showToast("Некорректный ник");
            case IN_GAME -> showToast("Нельзя менять в игре");
            case FAILED -> showToast("Ошибка смены");
        }
    }

    private void moveSelectedActiveToDeleted() {
        AltAccount selected = selectedAccount();
        if (selected == null) return;

        int activeIndex = activeAccounts.indexOf(selected);
        if (activeIndex < 0) return;

        AltAccountController.Snapshot snap = accountController.snapshot();
        UiStateSnapshot ui = captureUiState();

        AltAccount removed = accountController.moveToDeleted(activeIndex);
        if (removed == null) return;

        clampState();
        if (!persistAccounts(snap, ui)) return;

        rebuildFiltered();
        updateWidgets();
        showToast("x " + removed.name);
    }

    private void restoreSelectedDeleted() {
        AltAccount selected = selectedAccount();
        if (!showingDeleted || selected == null) return;

        int deletedIndex = deletedAccounts.indexOf(selected);
        if (deletedIndex < 0) return;

        AltAccountController.Snapshot snap = accountController.snapshot();
        UiStateSnapshot ui = captureUiState();

        AltAccount restored = accountController.restoreDeleted(deletedIndex, sortMode);
        if (restored == null) return;

        showingDeleted = false;
        selectedActiveIndex = accountController.findActiveIndex(restored.name);

        if (!persistAccounts(snap, ui)) return;

        rebuildFiltered();
        ensureSelectionVisible();
        updateWidgets();
        showToast("<- " + restored.name);
    }

    private void deleteSelectedDeleted() {
        AltAccount selected = selectedAccount();
        if (!showingDeleted || selected == null) return;

        int deletedIndex = deletedAccounts.indexOf(selected);
        if (deletedIndex < 0) return;

        AltAccountController.Snapshot snap = accountController.snapshot();
        UiStateSnapshot ui = captureUiState();

        String removed = accountController.deleteDeletedPermanently(deletedIndex);
        if (removed == null) return;

        clampState();
        if (!persistAccounts(snap, ui)) return;

        rebuildFiltered();
        updateWidgets();
        showToast("x " + removed);
    }

    // ===== LOAD/SAVE =====

    private void loadAccounts() {
        sortMode = accountController.load(sortMode);

        if (accountController.selectedActiveName() == null) {
            String preferred = AltStartupSessionSync.getPreferredStoredName();
            if (preferred != null) {
                AltAccountController.Snapshot snap = accountController.snapshot();
                accountController.setSelectedActiveName(preferred);
                if (!saveAccounts()) accountController.restore(snap);
            }
        }

        syncSelectionToCurrentSession();
        clampState();
        rebuildFiltered();
    }

    private void syncSelectionToCurrentSession() {
        String name = sessionService.getCurrentName(client);

        int idx = accountController.findActiveIndex(name);
        if (idx >= 0) {
            selectedActiveIndex = idx;
        } else if (accountController.selectedActiveName() != null) {
            selectedActiveIndex = accountController.findActiveIndex(accountController.selectedActiveName());
        }

        if (selectedActiveIndex < 0 && !activeAccounts.isEmpty()) selectedActiveIndex = 0;
        if (selectedDeletedIndex < 0 && !deletedAccounts.isEmpty()) selectedDeletedIndex = 0;

        ensureSelectionVisible();
    }

    private boolean saveAccounts() {
        if (!accountController.save(sortMode)) {
            showToast("Ошибка сохранения");
            return false;
        }
        AltStartupSessionSync.refresh();
        return true;
    }

    private boolean persistAccounts(AltAccountController.Snapshot snap, UiStateSnapshot ui) {
        accountController.setSelectedActiveName(selectedActiveName());
        if (saveAccounts()) return true;

        accountController.restore(snap);
        restoreUiState(ui);
        clampState();
        rebuildFiltered();
        updateWidgets();
        return false;
    }

    private UiStateSnapshot captureUiState() {
        return new UiStateSnapshot(showingDeleted, selectedActiveIndex, selectedDeletedIndex, searchText);
    }

    private void restoreUiState(UiStateSnapshot s) {
        if (s == null) return;
        showingDeleted = s.showingDeleted();
        selectedActiveIndex = s.selectedActiveIndex();
        selectedDeletedIndex = s.selectedDeletedIndex();
        searchText = s.searchText();
        if (searchField != null) searchField.setText(searchText);
    }

    private void updateWidgets() {
        clampState();

        if (loginButton == null || toggleButton == null || addButton == null
                || switchButton == null || deleteButton == null || doneButton == null) {
            return;
        }

        boolean hasSel = selectedAccount() != null;
        boolean activeMode = !showingDeleted;

        // подписи “как на примере”
        loginButton.setMessage(Text.literal(showingDeleted ? "Restore" : "Login"));
        loginButton.active = hasSel;

        toggleButton.setMessage(Text.literal(showingDeleted ? "Accounts" : "Edit"));

        addButton.setMessage(Text.literal("Add Account"));
        addButton.visible = activeMode;
        addButton.active = activeMode;

        switchButton.setMessage(Text.literal("Login (Offline Mode)"));
        switchButton.active = hasSel && activeMode;

        deleteButton.setMessage(Text.literal("Delete"));
        deleteButton.active = hasSel;

        doneButton.setMessage(Text.literal("Cancel"));
    }

    private void rebuildFiltered() {
        filteredAccounts.clear();

        String query = searchText.trim().toLowerCase(Locale.ROOT);
        for (AltAccount acc : currentAccounts()) {
            if (query.isEmpty() || acc.name.toLowerCase(Locale.ROOT).contains(query)) {
                filteredAccounts.add(acc);
            }
        }

        if (listWidget != null) {
            listWidget.setEntries(filteredAccounts);
            AltAccount rawSel = selectedRawAccount();
            if (rawSel != null && filteredAccounts.contains(rawSel)) {
                listWidget.selectAccount(rawSel);
            } else {
                listWidget.clearSelection();
            }
        }
    }

    private List<AltAccount> currentAccounts() {
        return showingDeleted ? deletedAccounts : activeAccounts;
    }

    private AltAccount selectedAccount() {
        AltAccount selected = selectedRawAccount();
        return (selected != null && filteredAccounts.contains(selected)) ? selected : null;
    }

    private AltAccount selectedRawAccount() {
        int idx = selectedIndex();
        List<AltAccount> list = currentAccounts();
        return (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
    }

    private void selectAccount(AltAccount account) {
        if (account == null) {
            setSelectedIndex(-1);
            if (listWidget != null) listWidget.clearSelection();
            return;
        }
        setSelectedIndex(currentAccounts().indexOf(account));
        if (listWidget != null) listWidget.selectAccount(account);
    }

    private int selectedIndex() {
        return showingDeleted ? selectedDeletedIndex : selectedActiveIndex;
    }

    private void setSelectedIndex(int idx) {
        if (showingDeleted) selectedDeletedIndex = idx;
        else selectedActiveIndex = idx;
    }

    private String selectedActiveName() {
        return (selectedActiveIndex >= 0 && selectedActiveIndex < activeAccounts.size())
                ? activeAccounts.get(selectedActiveIndex).name
                : null;
    }

    private void ensureSelectionVisible() {
        AltAccount sel = selectedRawAccount();
        if (sel == null) return;
        if (listWidget != null && filteredAccounts.contains(sel)) {
            listWidget.selectAccount(sel);
        }
    }

    private void clampState() {
        if (activeAccounts.isEmpty()) {
            selectedActiveIndex = -1;
        } else if (selectedActiveIndex >= activeAccounts.size()) {
            selectedActiveIndex = activeAccounts.size() - 1;
        }

        if (deletedAccounts.isEmpty()) {
            selectedDeletedIndex = -1;
            if (showingDeleted) showingDeleted = false;
        } else if (selectedDeletedIndex >= deletedAccounts.size()) {
            selectedDeletedIndex = deletedAccounts.size() - 1;
        }
    }

    private void showToast(String msg) {
        if (client == null) return;
        client.getToastManager().add(SystemToast.create(
                client,
                SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.literal("Alt Manager"),
                Text.literal(msg)
        ));
    }

    // ===== UI HELPERS =====

    private void drawPanel(DrawContext ctx, int x1, int y1, int x2, int y2) {
        ctx.fill(x1, y1, x2, y2, PANEL_BG);
        ctx.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, PANEL_INNER);

        ctx.fill(x1, y1, x2, y1 + 1, PANEL_BORDER);
        ctx.fill(x1, y2 - 1, x2, y2, PANEL_BORDER);
        ctx.fill(x1, y1, x1 + 1, y2, PANEL_BORDER);
        ctx.fill(x2 - 1, y1, x2, y2, PANEL_BORDER);
    }

    private SkinTextures resolveSkin(String name) {
        if (client == null || name == null || name.isBlank()) {
            UUID uuid = UUID.randomUUID();
            return DefaultSkinHelper.getSkinTextures(uuid);
        }

        return skinCache.computeIfAbsent(name, n -> {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes(StandardCharsets.UTF_8));
            GameProfile profile = new GameProfile(uuid, n);

            SkinTextures textures;
            try {
                textures = client.getSkinProvider().getSkinTextures(profile);
            } catch (Throwable t) {
                textures = null;
            }
            if (textures == null) textures = DefaultSkinHelper.getSkinTextures(uuid);
            return textures;
        });
    }

    // “3D” как на примере: полный персонаж из скина (2D-компоновка частей + оверлеи)
    private void drawFullSkin(DrawContext ctx, String name, int x, int y, int scale) {
        SkinTextures st = resolveSkin(name);
        Identifier skin = st.texture();
        int s = scale;

        int headX = x + 4 * s;
        int headY = y;

        int bodyX = x + 4 * s;
        int bodyY = y + 8 * s;

        int armR_X = x;
        int armY = y + 8 * s;

        int armL_X = x + 12 * s;

        int legR_X = x + 4 * s;
        int legY = y + 20 * s;

        int legL_X = x + 8 * s;

        // HEAD
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 8.0f, 8.0f, 8 * s, 8 * s, 64, 64);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 40.0f, 8.0f, 8 * s, 8 * s, 64, 64);

        // BODY
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, bodyX, bodyY, 20.0f, 20.0f, 8 * s, 12 * s, 64, 64);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, bodyX, bodyY, 20.0f, 36.0f, 8 * s, 12 * s, 64, 64);

        // RIGHT ARM
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, armR_X, armY, 44.0f, 20.0f, 4 * s, 12 * s, 64, 64);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, armR_X, armY, 44.0f, 36.0f, 4 * s, 12 * s, 64, 64);

        // LEFT ARM (64x64 classic)
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, armL_X, armY, 36.0f, 52.0f, 4 * s, 12 * s, 64, 64);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, armL_X, armY, 52.0f, 52.0f, 4 * s, 12 * s, 64, 64);

        // RIGHT LEG
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, legR_X, legY, 4.0f, 20.0f, 4 * s, 12 * s, 64, 64);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, legR_X, legY, 4.0f, 36.0f, 4 * s, 12 * s, 64, 64);

        // LEFT LEG
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, legL_X, legY, 20.0f, 52.0f, 4 * s, 12 * s, 64, 64);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, legL_X, legY, 4.0f, 52.0f, 4 * s, 12 * s, 64, 64);
    }

    // ===== VANILLA LIST WIDGET =====

    private final class AltListWidget extends AlwaysSelectedEntryListWidget<AltListWidget.Entry> {
        private final int left;

        AltListWidget(MinecraftClient client, int left, int width, int top, int bottom, int itemHeight) {
            super(client, width, bottom - top, top, bottom, itemHeight);
            this.left = left;
            trySetX(left);
        }

        void setEntries(List<AltAccount> accounts) {
            clearEntries();
            for (AltAccount acc : accounts) addEntry(new Entry(acc));
        }

        void selectAccount(AltAccount account) {
            if (account == null) {
                clearSelection();
                return;
            }
            for (Entry e : children()) {
                if (e.account == account) {
                    setSelected(e);
                    ensureVisible(e);
                    return;
                }
            }
            clearSelection();
        }

        void clearSelection() {
            setSelected(null);
        }

        @Override
        public int getRowLeft() {
            return left;
        }

        @Override
        public int getRowWidth() {
            return getListWidth();
        }

        @Override
        protected int getScrollbarX() {
            return left + getRowWidth() - 6;
        }

        private void trySetX(int x) {
            try {
                var m = this.getClass().getSuperclass().getMethod("setX", int.class);
                m.invoke(this, x);
            } catch (Throwable ignored) {
            }
        }

        final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            final AltAccount account;

            Entry(AltAccount account) {
                this.account = account;
            }

            @Override
            public void render(DrawContext ctx, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float delta) {

                // подсветка как ваниль, без даты
                boolean selected = AltManagerScreen.this.selectedRawAccount() == account;
                int bg = selected ? 0x50FFFFFF : (hovered ? 0x22FFFFFF : 0x14000000);
                ctx.fill(x, y, x + entryWidth, y + entryHeight, bg);

                ctx.drawTextWithShadow(textRenderer, Text.literal(account.name), x + 6, y + 7, 0xFFFFFF);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                AltListWidget.this.setSelected(this);
                AltManagerScreen.this.selectAccount(account);
                AltManagerScreen.this.updateWidgets();
                return true;
            }

            @Override
            public Text getNarration() {
                return Text.literal(account.name);
            }
        }
    }

    // ===== DATA / STORAGE / SESSION (твой код как был) =====

    public static final class AltAccount {
        private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");

        public final String name;
        public final String date;
        public final long createdAt;
        public boolean pinned;
        public int timesUsed;
        public long lastUsedAt;

        public AltAccount(String name, String date, long createdAt, boolean pinned) {
            this.name = name;
            this.date = date;
            this.createdAt = createdAt;
            this.pinned = pinned;
            this.timesUsed = 0;
            this.lastUsedAt = 0L;
        }

        public AltAccount(String name, String date, long createdAt, boolean pinned, int timesUsed, long lastUsedAt) {
            this.name = name;
            this.date = date;
            this.createdAt = createdAt;
            this.pinned = pinned;
            this.timesUsed = timesUsed;
            this.lastUsedAt = lastUsedAt;
        }

        public static AltAccount create(String name) {
            return new AltAccount(name, LocalDateTime.now().format(DATE_FORMAT), System.currentTimeMillis(), false, 0, 0L);
        }

        public AltAccount copy() {
            return new AltAccount(name, date, createdAt, pinned, timesUsed, lastUsedAt);
        }

        public void markUsed() {
            timesUsed++;
            lastUsedAt = System.currentTimeMillis();
        }

        public String getLastUsedFormatted() {
            if (lastUsedAt <= 0) return "Никогда";
            return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(lastUsedAt),
                    java.time.ZoneId.systemDefault()
            ).format(DATE_FORMAT);
        }
    }

    public enum AltSortMode {
        NEWEST,
        OLDEST,
        AZ,
        ZA;

        public Comparator<AltAccount> comparator() {
            return switch (this) {
                case OLDEST -> Comparator.comparingLong(account -> account.createdAt);
                case AZ -> Comparator.comparing(account -> account.name.toLowerCase(Locale.ROOT));
                case ZA -> (a, b) -> b.name.compareToIgnoreCase(a.name);
                case NEWEST -> (a, b) -> Long.compare(b.createdAt, a.createdAt);
            };
        }
    }

    public static final class AltAccountStore {
        private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
        private final File storageFile;

        public AltAccountStore(File storageFile) {
            this.storageFile = storageFile;
        }

        public LoadedAccounts load() {
            LoadedAccounts fromPrimary = tryLoad(storageFile.toPath());
            if (fromPrimary != null) return fromPrimary;

            Path tempPath = tempFilePath();
            LoadedAccounts fromTemp = tryLoad(tempPath);
            if (fromTemp != null) {
                Strange.LOGGER.warn("Recovered alt storage from temporary file {}", tempPath.toAbsolutePath());
                promoteRecoveredTempFile(tempPath, storageFile.toPath());
                return fromTemp;
            }

            return emptyAccounts();
        }

        private LoadedAccounts tryLoad(Path sourcePath) {
            if (sourcePath == null || !Files.isRegularFile(sourcePath)) return null;

            List<AltAccount> active = new ArrayList<>();
            List<AltAccount> deleted = new ArrayList<>();
            String selectedActiveName = null;

            try (Reader reader = Files.newBufferedReader(sourcePath, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);

                if (parsed.isJsonArray()) {
                    readAccounts(parsed.getAsJsonArray(), active, deleted, sourcePath);
                    return new LoadedAccounts(active, deleted, null, null);
                }

                if (!parsed.isJsonObject()) {
                    Strange.LOGGER.warn("Invalid alt storage format in {}", sourcePath.toAbsolutePath());
                    return null;
                }

                JsonObject root = parsed.getAsJsonObject();
                if (root.has("selectedActiveName") && root.get("selectedActiveName").isJsonPrimitive()) {
                    selectedActiveName = root.get("selectedActiveName").getAsString();
                }
                AltSortMode sortMode = readSortMode(root);

                JsonElement accountsElement = root.get("accounts");
                if (accountsElement != null && accountsElement.isJsonArray()) {
                    readAccounts(accountsElement.getAsJsonArray(), active, deleted, sourcePath);
                } else {
                    Strange.LOGGER.warn("Missing accounts array in {}", sourcePath.toAbsolutePath());
                }

                return new LoadedAccounts(active, deleted, sanitizeSelectedName(active, selectedActiveName), sortMode);
            } catch (IOException | RuntimeException exception) {
                Strange.LOGGER.warn("Failed to load accounts from {}", sourcePath.toAbsolutePath(), exception);
                return null;
            }
        }

        public boolean save(List<AltAccount> active, List<AltAccount> deleted, String selectedActiveName, AltSortMode sortMode) {
            try {
                Path storagePath = storageFile.toPath();
                Path parent = storagePath.getParent();
                if (parent != null) Files.createDirectories(parent);

                String sanitizedSelectedName = sanitizeSelectedName(active, selectedActiveName);

                JsonArray array = new JsonArray();
                appendAccounts(array, active, false);
                appendAccounts(array, deleted, true);

                JsonObject root = new JsonObject();
                root.addProperty("version", 3);
                if (sanitizedSelectedName != null) root.addProperty("selectedActiveName", sanitizedSelectedName);
                if (sortMode != null) root.addProperty("sortMode", sortMode.name());
                root.add("accounts", array);

                AtomicFileIO.writeUtf8Atomically(storagePath, writer -> PRETTY_GSON.toJson(root, writer));
                return true;
            } catch (IOException e) {
                Strange.LOGGER.warn("Failed to save accounts to {}", storageFile.getAbsolutePath(), e);
                return false;
            }
        }

        private static void readAccounts(JsonArray array, List<AltAccount> active, List<AltAccount> deleted, Path sourcePath) {
            Set<String> activeNames = new HashSet<>();
            Set<String> deletedNames = new HashSet<>();

            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (!element.isJsonObject()) {
                    Strange.LOGGER.warn("Skipping invalid alt account entry {} in {}", i, sourcePath.toAbsolutePath());
                    continue;
                }

                try {
                    JsonObject object = element.getAsJsonObject();
                    AltAccount account = readAccount(object);
                    if (account == null) {
                        Strange.LOGGER.warn("Skipping incomplete alt account entry {} in {}", i, sourcePath.toAbsolutePath());
                        continue;
                    }

                    boolean isDeleted = readBooleanProperty(object, "deleted");
                    String normalizedName = normalizeAccountName(account.name);

                    if (isDeleted) {
                        if (activeNames.contains(normalizedName) || !deletedNames.add(normalizedName)) {
                            Strange.LOGGER.warn("Skipping duplicate deleted alt account {} in {}", account.name, sourcePath.toAbsolutePath());
                            continue;
                        }
                        deleted.add(account);
                    } else {
                        if (!activeNames.add(normalizedName)) {
                            Strange.LOGGER.warn("Skipping duplicate active alt account {} in {}", account.name, sourcePath.toAbsolutePath());
                            continue;
                        }

                        if (deletedNames.remove(normalizedName)) {
                            removeAccountByName(deleted, account.name);
                            Strange.LOGGER.warn("Preferring active alt account {} over deleted duplicate in {}", account.name, sourcePath.toAbsolutePath());
                        }
                        active.add(account);
                    }
                } catch (RuntimeException exception) {
                    Strange.LOGGER.warn("Skipping malformed alt account entry {} in {}", i, sourcePath.toAbsolutePath(), exception);
                }
            }
        }

        private static AltAccount readAccount(JsonObject object) {
            if (!object.has("name") || !object.get("name").isJsonPrimitive()) return null;

            String name = sanitizeStoredName(object.get("name").getAsString());
            if (name == null) return null;

            String date = object.has("date")
                    && object.get("date").isJsonPrimitive()
                    && object.get("date").getAsJsonPrimitive().isString()
                    ? object.get("date").getAsString().trim()
                    : "";

            long createdAt = readCreatedAt(object);
            boolean pinned = readBooleanProperty(object, "pinned");
            int timesUsed = readIntProperty(object, "timesUsed");
            long lastUsedAt = readLongProperty(object, "lastUsedAt");

            return new AltAccount(name, date, createdAt, pinned, timesUsed, lastUsedAt);
        }

        private static String sanitizeSelectedName(List<AltAccount> active, String selectedActiveName) {
            if (selectedActiveName == null || selectedActiveName.isBlank()) return null;
            for (AltAccount account : active) {
                if (account.name.equalsIgnoreCase(selectedActiveName)) return account.name;
            }
            return null;
        }

        private static AltSortMode readSortMode(JsonObject root) {
            if (root == null || !root.has("sortMode") || !root.get("sortMode").isJsonPrimitive()) return null;
            try {
                return AltSortMode.valueOf(root.get("sortMode").getAsString());
            } catch (IllegalArgumentException exception) {
                Strange.LOGGER.warn("Skipping unknown alt sort mode {}", root.get("sortMode").getAsString(), exception);
                return null;
            }
        }

        private static void appendAccounts(JsonArray array, List<AltAccount> accounts, boolean deleted) {
            for (AltAccount account : accounts) {
                String storedName = sanitizeStoredName(account.name);
                if (storedName == null) {
                    Strange.LOGGER.warn("Skipping invalid in-memory alt account during save to {}", deleted ? "deleted" : "active");
                    continue;
                }

                JsonObject object = new JsonObject();
                object.addProperty("name", storedName);
                object.addProperty("date", account.date);
                object.addProperty("createdAt", account.createdAt);
                object.addProperty("pinned", account.pinned);
                object.addProperty("timesUsed", account.timesUsed);
                object.addProperty("lastUsedAt", account.lastUsedAt);
                object.addProperty("deleted", deleted);
                array.add(object);
            }
        }

        private static boolean readBooleanProperty(JsonObject object, String propertyName) {
            return object != null
                    && propertyName != null
                    && object.has(propertyName)
                    && object.get(propertyName).isJsonPrimitive()
                    && object.get(propertyName).getAsJsonPrimitive().isBoolean()
                    && object.get(propertyName).getAsBoolean();
        }

        private static int readIntProperty(JsonObject object, String propertyName) {
            if (object == null || propertyName == null || !object.has(propertyName)) return 0;
            var el = object.get(propertyName);
            return el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber() ? el.getAsInt() : 0;
        }

        private static long readLongProperty(JsonObject object, String propertyName) {
            if (object == null || propertyName == null || !object.has(propertyName)) return 0L;
            var el = object.get(propertyName);
            return el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber() ? el.getAsLong() : 0L;
        }

        private static long readCreatedAt(JsonObject object) {
            long fallback = System.currentTimeMillis();
            if (object == null || !object.has("createdAt") || !object.get("createdAt").isJsonPrimitive()) return fallback;

            var primitive = object.get("createdAt").getAsJsonPrimitive();
            if (!primitive.isNumber()) return fallback;

            long createdAt = primitive.getAsLong();
            return createdAt > 0L ? createdAt : fallback;
        }

        private static String sanitizeStoredName(String name) {
            if (name == null) return null;
            String trimmed = name.trim();
            return trimmed.isBlank() ? null : trimmed;
        }

        private static String normalizeAccountName(String name) {
            String sanitized = sanitizeStoredName(name);
            return sanitized == null ? "" : sanitized.toLowerCase(Locale.ROOT);
        }

        private static void removeAccountByName(List<AltAccount> accounts, String name) {
            String normalizedName = normalizeAccountName(name);
            for (int index = 0; index < accounts.size(); index++) {
                if (normalizeAccountName(accounts.get(index).name).equals(normalizedName)) {
                    accounts.remove(index);
                    return;
                }
            }
        }

        private Path tempFilePath() {
            return AtomicFileIO.tempPath(storageFile.toPath());
        }

        private void promoteRecoveredTempFile(Path tempPath, Path storagePath) {
            try {
                AtomicFileIO.moveReplace(tempPath, storagePath);
            } catch (IOException exception) {
                Strange.LOGGER.warn("Failed to promote recovered alt temp file {}", tempPath.toAbsolutePath(), exception);
            }
        }

        private static LoadedAccounts emptyAccounts() {
            return new LoadedAccounts(new ArrayList<>(), new ArrayList<>(), null, null);
        }

        public record LoadedAccounts(List<AltAccount> active, List<AltAccount> deleted, String selectedActiveName, AltSortMode sortMode) {}
    }

    public static final class AltAccountController {
        private final AltAccountStore accountStore;
        private final List<AltAccount> activeAccounts = new ArrayList<>();
        private final List<AltAccount> deletedAccounts = new ArrayList<>();
        private final List<AltAccount> activeAccountsView = Collections.unmodifiableList(activeAccounts);
        private final List<AltAccount> deletedAccountsView = Collections.unmodifiableList(deletedAccounts);
        private String selectedActiveName;

        public AltAccountController(AltAccountStore accountStore) {
            this.accountStore = accountStore;
        }

        public List<AltAccount> activeAccounts() {
            return activeAccountsView;
        }

        public List<AltAccount> deletedAccounts() {
            return deletedAccountsView;
        }

        public String selectedActiveName() {
            return selectedActiveName;
        }

        public void setSelectedActiveName(String selectedActiveName) {
            this.selectedActiveName = sanitizeSelectedActiveName(selectedActiveName);
        }

        public AltSortMode load(AltSortMode fallbackSortMode) {
            AltAccountStore.LoadedAccounts loaded = accountStore.load();

            activeAccounts.clear();
            activeAccounts.addAll(loaded.active());

            deletedAccounts.clear();
            deletedAccounts.addAll(loaded.deleted());

            selectedActiveName = loaded.selectedActiveName();

            AltSortMode resolvedSortMode = loaded.sortMode() != null ? loaded.sortMode() : fallbackSortMode;
            sortActive(resolvedSortMode);
            reconcileSelectedActiveName();
            return resolvedSortMode;
        }

        public boolean save(AltSortMode sortMode) {
            reconcileSelectedActiveName();
            return accountStore.save(activeAccounts, deletedAccounts, selectedActiveName, sortMode);
        }

        public Snapshot snapshot() {
            return new Snapshot(copyAccounts(activeAccounts), copyAccounts(deletedAccounts), selectedActiveName);
        }

        public void restore(Snapshot snapshot) {
            if (snapshot == null) return;

            activeAccounts.clear();
            activeAccounts.addAll(copyAccounts(snapshot.activeAccounts()));

            deletedAccounts.clear();
            deletedAccounts.addAll(copyAccounts(snapshot.deletedAccounts()));

            selectedActiveName = snapshot.selectedActiveName();
            reconcileSelectedActiveName();
        }

        public void sortActive(AltSortMode sortMode) {
            Comparator<AltAccount> comparator = Comparator
                    .comparing((AltAccount account) -> !account.pinned)
                    .thenComparing(sortMode.comparator());
            activeAccounts.sort(comparator);
        }

        public boolean containsName(String name) {
            return findIndex(activeAccounts, name) != -1 || findIndex(deletedAccounts, name) != -1;
        }

        public AltAccount createAccount(String name, AltSortMode sortMode) {
            AltAccount account = AltAccount.create(name);
            activeAccounts.add(account);
            sortActive(sortMode);
            return account;
        }

        public AltAccount moveToDeleted(int index) {
            if (index < 0 || index >= activeAccounts.size()) return null;
            AltAccount removedAccount = activeAccounts.remove(index);
            deletedAccounts.add(0, removedAccount);
            reconcileSelectedActiveName();
            return removedAccount;
        }

        public AltAccount restoreDeleted(int deletedIndex, AltSortMode sortMode) {
            if (deletedIndex < 0 || deletedIndex >= deletedAccounts.size()) return null;
            AltAccount restored = deletedAccounts.remove(deletedIndex);
            activeAccounts.add(restored);
            sortActive(sortMode);
            return restored;
        }

        public String deleteDeletedPermanently(int deletedIndex) {
            if (deletedIndex < 0 || deletedIndex >= deletedAccounts.size()) return null;
            return deletedAccounts.remove(deletedIndex).name;
        }

        public int findActiveIndex(String name) {
            return findIndex(activeAccounts, name);
        }

        private static int findIndex(List<AltAccount> entries, String name) {
            if (name == null) return -1;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).name.equalsIgnoreCase(name)) return i;
            }
            return -1;
        }

        private String sanitizeSelectedActiveName(String name) {
            int index = findIndex(activeAccounts, name);
            return index == -1 ? null : activeAccounts.get(index).name;
        }

        private void reconcileSelectedActiveName() {
            selectedActiveName = sanitizeSelectedActiveName(selectedActiveName);
        }

        private static List<AltAccount> copyAccounts(List<AltAccount> accounts) {
            List<AltAccount> copy = new ArrayList<>(accounts.size());
            for (AltAccount account : accounts) copy.add(account.copy());
            return copy;
        }

        public record Snapshot(List<AltAccount> activeAccounts, List<AltAccount> deletedAccounts, String selectedActiveName) {}
    }

    public static final class AltSessionService {
        private static final Pattern OFFLINE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
        private static final String DEFAULT_USERNAME = "Player";

        public enum SwitchResult {
            SUCCESS,
            INVALID_NAME,
            IN_GAME,
            FAILED
        }

        public record SessionSnapshot(Session session, String username) {}

        public SwitchResult switchToOfflineProfile(MinecraftClient client, String name) {
            if (client == null) return SwitchResult.FAILED;
            if (client.world != null) return SwitchResult.IN_GAME;

            String normalizedName = normalizeName(name);
            if (normalizedName == null) return SwitchResult.INVALID_NAME;

            Session session = new Session(
                    normalizedName,
                    UUID.nameUUIDFromBytes(("OfflinePlayer:" + normalizedName).getBytes(StandardCharsets.UTF_8)),
                    "0",
                    Optional.empty(),
                    Optional.empty(),
                    Session.AccountType.LEGACY
            );

            return applySession(client, session, normalizedName) ? SwitchResult.SUCCESS : SwitchResult.FAILED;
        }

        public SessionSnapshot captureSession(MinecraftClient client) {
            if (client == null) return new SessionSnapshot(null, DEFAULT_USERNAME);
            Session session = client.getSession();
            return new SessionSnapshot(session, getCurrentName(client));
        }

        public boolean restoreSession(MinecraftClient client, SessionSnapshot snapshot) {
            if (snapshot == null || snapshot.session() == null) return false;
            return applySession(client, snapshot.session(), snapshot.username());
        }

        public boolean isCurrentProfile(MinecraftClient client, String name) {
            String normalizedName = normalizeName(name);
            return normalizedName != null && normalizedName.equalsIgnoreCase(getCurrentName(client));
        }

        public String getCurrentName(MinecraftClient client) {
            if (client == null || client.getSession() == null) return DEFAULT_USERNAME;
            String username = client.getSession().getUsername();
            return username == null || username.isBlank() ? DEFAULT_USERNAME : username;
        }

        public String normalizeName(String name) {
            if (name == null) return null;
            String normalizedName = name.trim();
            if (!OFFLINE_NAME_PATTERN.matcher(normalizedName).matches()) return null;
            return normalizedName;
        }

        private boolean applySession(MinecraftClient client, Session session, String expectedVisibleName) {
            if (client == null || session == null) return false;

            if (!(client instanceof MinecraftClientAccessor accessor)) {
                Strange.LOGGER.warn("MinecraftClient accessor is unavailable during session switch");
                return false;
            }

            accessor.setSession(session);
            refreshClientStateAfterSessionChange();

            Session appliedSession = client.getSession();
            if (appliedSession == null) {
                Strange.LOGGER.warn("Session switch produced a null client session");
                return false;
            }

            if (expectedVisibleName != null && !expectedVisibleName.equalsIgnoreCase(appliedSession.getUsername())) {
                Strange.LOGGER.debug("Session applied with different visible name: expected={}, actual={}",
                        expectedVisibleName, appliedSession.getUsername());
            }
            return true;
        }

        private void refreshClientStateAfterSessionChange() {
            ServerUtil.invalidateCache();
        }
    }
}