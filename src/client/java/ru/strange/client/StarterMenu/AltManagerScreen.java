package ru.strange.client.StarterMenu;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public class AltManagerScreen extends Screen {

    private static final int ROW_HEIGHT     = 36;
    private static final int HEAD_SIZE      = 24;
    private static final int BTN_H          = 20;
    private static final int BTN_W          = 110;
    private static final int GAP            = 6;
    private static final int BOTTOM_PANEL_H = 36;

    private static final File ALTS_FILE = new File(Strange.root, "alts.json");

    private final Map<String, SkinTextures> skinCache = new HashMap<>();
    private final Screen               parent;
    private final AltAccountController accountController =
            new AltAccountController(new AltAccountStore(ALTS_FILE));
    private final AltSessionService    sessionService = new AltSessionService();
    private final List<AltAccount>     activeAccounts  = accountController.activeAccounts();
    private final List<AltAccount>     deletedAccounts = accountController.deletedAccounts();

    private ButtonWidget useButton, addButton, deleteButton, cancelButton;

    private AltSortMode sortMode            = AltSortMode.NEWEST;
    private int         selectedActiveIndex = -1;
    private int         scrollOffset        = 0;

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Менеджер аккаунтов"));
        this.parent = parent;
        loadAccounts();
    }

    private int listTop()    { return 0; }
    private int listBottom() { return height - BOTTOM_PANEL_H; }
    private int listHeight() { return listBottom() - listTop(); }

    private int maxScroll() {
        int contentH = activeAccounts.size() * ROW_HEIGHT;
        return Math.max(0, contentH - listHeight());
    }

    @Override
    protected void init() {
        super.init();

        int totalBtnW = BTN_W * 4 + GAP * 3;
        int rowY      = height - BOTTOM_PANEL_H + (BOTTOM_PANEL_H - BTN_H) / 2;
        int x0        = width / 2 - totalBtnW / 2;

        useButton    = addDrawableChild(btn(x0,                     rowY, BTN_W, "Войти",      b -> useSelectedProfile()));
        addButton    = addDrawableChild(btn(x0 + (BTN_W + GAP),     rowY, BTN_W, "Добавить",   b -> addAccount()));
        deleteButton = addDrawableChild(btn(x0 + (BTN_W + GAP) * 2, rowY, BTN_W, "Удалить",    b -> handleDeleteAction()));
        cancelButton = addDrawableChild(btn(x0 + (BTN_W + GAP) * 3, rowY, BTN_W, "Закрыть",   b -> close()));

        clampState();
        updateWidgets();
    }

    private ButtonWidget btn(int x, int y, int w, String label, ButtonWidget.PressAction a) {
        return ButtonWidget.builder(Text.literal(label), a).dimensions(x, y, w, BTN_H).build();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);

        int lt = listTop();
        int lb = listBottom();

        int contentH = activeAccounts.size() * ROW_HEIGHT;
        int visibleH = Math.min(contentH, listHeight());
        if (visibleH > 0) {
            ctx.fill(0, lt, width, lt + visibleH, 0xAA1A1A1A);
        }

        ctx.enableScissor(0, lt, width, lb);
        for (int i = 0; i < activeAccounts.size(); i++) {
            int rowY = lt + i * ROW_HEIGHT - scrollOffset;
            if (rowY + ROW_HEIGHT < lt) continue;
            if (rowY > lb) break;
            renderRow(ctx, i, rowY, mouseX, mouseY);
        }
        ctx.disableScissor();

        renderScrollbar(ctx);

        ctx.fill(0, lb, width, height, 0xCC000000);

        if (activeAccounts.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Нет аккаунтов. Нажмите \"Добавить\"."),
                    width / 2, (lt + lb) / 2 - 4, 0xA0A0A0);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderRow(DrawContext ctx, int index, int rowY, int mouseX, int mouseY) {
        boolean hovered  = mouseX >= 0 && mouseX < width
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                && mouseY >= listTop() && mouseY < listBottom();
        boolean selected = index == selectedActiveIndex;

        if (selected) {
            ctx.fill(0, rowY, width, rowY + ROW_HEIGHT, 0xBB555555);
        } else if (hovered) {
            ctx.fill(0, rowY, width, rowY + ROW_HEIGHT, 0x44FFFFFF);
        }

        ctx.fill(0, rowY + ROW_HEIGHT - 1, width, rowY + ROW_HEIGHT, 0x33FFFFFF);

        AltAccount acc = activeAccounts.get(index);

        int ax = 4;
        int ay = rowY + (ROW_HEIGHT - HEAD_SIZE) / 2;
        drawHead(ctx, acc.name, ax, ay, HEAD_SIZE);

        int textX = ax + HEAD_SIZE + 8;
        int midY  = rowY + ROW_HEIGHT / 2;
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(acc.name),
                textX, midY - textRenderer.fontHeight + 1, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(offlineUuid(acc.name).toString()),
                textX, midY + 2, 0xFF888888);
    }

    private void renderScrollbar(DrawContext ctx) {
        int max = maxScroll();
        if (max <= 0) return;

        int lt  = listTop();
        int lb  = listBottom();
        int lh  = listHeight();
        int sbX = width - 5;
        int sbW = 4;

        ctx.fill(sbX, lt, sbX + sbW, lb, 0x33FFFFFF);

        int   totalContent = activeAccounts.size() * ROW_HEIGHT;
        float ratio        = (float) lh / totalContent;
        int   thumbH       = Math.max(20, (int)(lh * ratio));
        int   thumbY       = lt + (int)((lh - thumbH) * ((float) scrollOffset / max));
        ctx.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xAAFFFFFF);
    }

    private long lastClickTime = 0;
    private int  lastClickIndex = -1;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY >= listTop() && mouseY < listBottom() && button == 0) {
            int index = getIndexAt(mouseX, mouseY);
            if (index >= 0) {
                long now = System.currentTimeMillis();

                if (index == lastClickIndex && (now - lastClickTime) < 300) {
                    useSelectedProfile();
                } else {
                    selectedActiveIndex = index;
                    updateWidgets();
                    lastClickTime = now;
                    lastClickIndex = index;
                }

                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (mouseY >= listTop() && mouseY < listBottom()) {
            scrollOffset -= (int)(verticalAmount * ROW_HEIGHT);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int getIndexAt(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseX >= width) return -1;
        int rel = (int) mouseY - listTop() + scrollOffset;
        int idx = rel / ROW_HEIGHT;
        if (idx >= 0 && idx < activeAccounts.size()) return idx;
        return -1;
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    private void ensureSelectedVisible() {
        if (selectedActiveIndex < 0) return;
        int rowTop = selectedActiveIndex * ROW_HEIGHT;
        int rowBot = rowTop + ROW_HEIGHT;
        int lh     = listHeight();
        if (rowTop < scrollOffset)      scrollOffset = rowTop;
        if (rowBot > scrollOffset + lh) scrollOffset = rowBot - lh;
        clampScroll();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE)                                      { close();              return true; }
        if (keyCode == GLFW.GLFW_KEY_UP)                                          { moveSelection(-1);    return true; }
        if (keyCode == GLFW.GLFW_KEY_DOWN)                                        { moveSelection(1);     return true; }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { useSelectedProfile(); return true; }
        if (keyCode == GLFW.GLFW_KEY_DELETE)                                      { handleDeleteAction(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client == null) return;
        Screen target = parent != null ? parent : new TitleScreen();
        if (target instanceof TitleScreen) StrangeVisualsClient.suppressTitleScreenReplacement(target);
        client.setScreen(target);
    }

    private void moveSelection(int dir) {
        if (activeAccounts.isEmpty()) return;
        int next = selectedActiveIndex < 0 ? 0
                : Math.max(0, Math.min(activeAccounts.size() - 1, selectedActiveIndex + dir));
        selectedActiveIndex = next;
        ensureSelectedVisible();
        updateWidgets();
    }

    private void addAccount() {
        if (client == null) return;
        client.setScreen(new AltAddAccountScreen(this, name -> {
            String normalized = sessionService.normalizeName(name);
            if (normalized == null)                          { showToast("Некорректный ник"); return; }
            if (accountController.containsName(normalized)) { showToast("Уже существует");   return; }
            AltAccountController.Snapshot snap = accountController.snapshot();
            accountController.createAccount(normalized, sortMode);
            if (!persistAccounts(snap)) return;
            updateWidgets();
            showToast("+ " + normalized);
        }));
    }

    private void useSelectedProfile() {
        AltAccount account = selectedAccount();
        if (account == null) return;

        AltAccountController.Snapshot     snap = accountController.snapshot();
        AltSessionService.SessionSnapshot prev = sessionService.captureSession(client);

        AltSessionService.SwitchResult result = sessionService.isCurrentProfile(client, account.name)
                ? AltSessionService.SwitchResult.SUCCESS
                : sessionService.switchToOfflineProfile(client, account.name);

        switch (result) {
            case SUCCESS -> {
                account.markUsed();
                accountController.setSelectedActiveName(account.name);
                if (!persistAccounts(snap)) {
                    sessionService.restoreSession(client, prev);
                    syncSelectionToCurrentSession();
                    return;
                }
                syncSelectionToCurrentSession();
                updateWidgets();
                showToast("Вошли как " + account.name);
            }
            case INVALID_NAME -> showToast("Некорректный ник");
            case IN_GAME      -> showToast("Нельзя менять в игре");
            case FAILED       -> showToast("Ошибка смены аккаунта");
        }
    }

    private void handleDeleteAction() {
        AltAccount selected = selectedAccount();
        if (selected == null) return;
        int activeIndex = activeAccounts.indexOf(selected);
        if (activeIndex < 0) return;

        AltAccountController.Snapshot snap    = accountController.snapshot();
        AltAccount                    removed = accountController.moveToDeleted(activeIndex);
        if (removed == null) return;

        int delIdx = deletedAccounts.indexOf(removed);
        if (delIdx >= 0) accountController.deleteDeletedPermanently(delIdx);

        clampState();
        if (!persistAccounts(snap)) return;
        clampScroll();
        updateWidgets();
        showToast("Удалён: " + removed.name);
    }

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
    }

    private void syncSelectionToCurrentSession() {
        String name = sessionService.getCurrentName(client);
        int idx = accountController.findActiveIndex(name);
        if (idx >= 0) {
            selectedActiveIndex = idx;
        } else if (accountController.selectedActiveName() != null) {
            selectedActiveIndex = accountController.findActiveIndex(
                    accountController.selectedActiveName());
        }
        if (selectedActiveIndex < 0 && !activeAccounts.isEmpty()) selectedActiveIndex = 0;
    }

    private boolean saveAccounts() {
        if (!accountController.save(sortMode)) {
            showToast("Ошибка сохранения");
            return false;
        }
        AltStartupSessionSync.refresh();
        return true;
    }

    private boolean persistAccounts(AltAccountController.Snapshot snap) {
        accountController.setSelectedActiveName(selectedActiveName());
        if (saveAccounts()) return true;
        accountController.restore(snap);
        clampState();
        return false;
    }

    private AltAccount selectedAccount() {
        if (selectedActiveIndex < 0 || selectedActiveIndex >= activeAccounts.size()) return null;
        return activeAccounts.get(selectedActiveIndex);
    }

    private String selectedActiveName() {
        return (selectedActiveIndex >= 0 && selectedActiveIndex < activeAccounts.size())
                ? activeAccounts.get(selectedActiveIndex).name : null;
    }

    private void clampState() {
        if (activeAccounts.isEmpty()) {
            selectedActiveIndex = -1;
        } else if (selectedActiveIndex >= activeAccounts.size()) {
            selectedActiveIndex = activeAccounts.size() - 1;
        } else if (selectedActiveIndex < 0) {
            selectedActiveIndex = 0;
        }
    }

    private void updateWidgets() {
        boolean hasSel = selectedAccount() != null;
        if (useButton    != null) useButton.active    = hasSel;
        if (deleteButton != null) deleteButton.active = hasSel;
    }

    private void showToast(String msg) {
        if (client == null) return;
        client.getToastManager().add(SystemToast.create(
                client, SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.literal("Менеджер аккаунтов"), Text.literal(msg)));
    }

    static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private SkinTextures resolveSkin(String name) {
        if (client == null || name == null || name.isBlank())
            return DefaultSkinHelper.getSkinTextures(UUID.randomUUID());
        return skinCache.computeIfAbsent(name, n -> {
            UUID        uuid    = offlineUuid(n);
            GameProfile profile = new GameProfile(uuid, n);
            SkinTextures tex;
            try   { tex = client.getSkinProvider().getSkinTextures(profile); }
            catch (Throwable t) { tex = null; }
            if (tex == null) tex = DefaultSkinHelper.getSkinTextures(uuid);
            return tex;
        });
    }

    private void drawHead(DrawContext ctx, String name, int x, int y, int size) {
        SkinTextures st   = resolveSkin(name);
        Identifier   skin = st.texture();
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin,
                x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin,
                x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64);
    }

    public static final class AltAccount {
        private static final DateTimeFormatter DATE_FORMAT =
                DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
        public final String name, date;
        public final long   createdAt;
        public boolean pinned;
        public int     timesUsed;
        public long    lastUsedAt;

        public AltAccount(String name, String date, long createdAt, boolean pinned) {
            this(name, date, createdAt, pinned, 0, 0L);
        }
        public AltAccount(String name, String date, long createdAt, boolean pinned,
                          int timesUsed, long lastUsedAt) {
            this.name = name; this.date = date; this.createdAt = createdAt;
            this.pinned = pinned; this.timesUsed = timesUsed; this.lastUsedAt = lastUsedAt;
        }
        public static AltAccount create(String name) {
            return new AltAccount(name, LocalDateTime.now().format(DATE_FORMAT),
                    System.currentTimeMillis(), false, 0, 0L);
        }
        public AltAccount copy() {
            return new AltAccount(name, date, createdAt, pinned, timesUsed, lastUsedAt);
        }
        public void markUsed() { timesUsed++; lastUsedAt = System.currentTimeMillis(); }
    }

    public enum AltSortMode {
        NEWEST, OLDEST, AZ, ZA;
        public Comparator<AltAccount> comparator() {
            return switch (this) {
                case OLDEST -> Comparator.comparingLong(a -> a.createdAt);
                case AZ     -> Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT));
                case ZA     -> (a, b) -> b.name.compareToIgnoreCase(a.name);
                case NEWEST -> (a, b) -> Long.compare(b.createdAt, a.createdAt);
            };
        }
    }

    public static final class AltAccountStore {
        private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
        private final File storageFile;

        public AltAccountStore(File storageFile) { this.storageFile = storageFile; }

        public LoadedAccounts load() {
            LoadedAccounts p = tryLoad(storageFile.toPath());
            if (p != null) return p;
            Path tmp = AtomicFileIO.tempPath(storageFile.toPath());
            LoadedAccounts t = tryLoad(tmp);
            if (t != null) {
                Strange.LOGGER.warn("Восстановлено из temp {}", tmp);
                try { AtomicFileIO.moveReplace(tmp, storageFile.toPath()); }
                catch (IOException e) { Strange.LOGGER.warn("Не удалось переместить temp", e); }
                return t;
            }
            return new LoadedAccounts(new ArrayList<>(), new ArrayList<>(), null, null);
        }

        private LoadedAccounts tryLoad(Path src) {
            if (src == null || !Files.isRegularFile(src)) return null;
            List<AltAccount> active = new ArrayList<>(), deleted = new ArrayList<>();
            String selName = null;
            try (Reader r = Files.newBufferedReader(src, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(r);
                if (parsed.isJsonArray()) {
                    readAccounts(parsed.getAsJsonArray(), active, deleted, src);
                    return new LoadedAccounts(active, deleted, null, null);
                }
                if (!parsed.isJsonObject()) return null;
                JsonObject root = parsed.getAsJsonObject();
                if (root.has("selectedActiveName")
                        && root.get("selectedActiveName").isJsonPrimitive())
                    selName = root.get("selectedActiveName").getAsString();
                AltSortMode sm = readSortMode(root);
                JsonElement ae = root.get("accounts");
                if (ae != null && ae.isJsonArray())
                    readAccounts(ae.getAsJsonArray(), active, deleted, src);
                return new LoadedAccounts(active, deleted, sanSel(active, selName), sm);
            } catch (Exception e) {
                Strange.LOGGER.warn("Ошибка загрузки {}", src, e);
                return null;
            }
        }

        public boolean save(List<AltAccount> active, List<AltAccount> deleted,
                            String selName, AltSortMode sortMode) {
            try {
                Path p   = storageFile.toPath();
                Path par = p.getParent();
                if (par != null) Files.createDirectories(par);
                JsonArray arr = new JsonArray();
                appendAccounts(arr, active, false);
                appendAccounts(arr, deleted, true);
                JsonObject root = new JsonObject();
                root.addProperty("version", 3);
                String san = sanSel(active, selName);
                if (san != null)      root.addProperty("selectedActiveName", san);
                if (sortMode != null) root.addProperty("sortMode", sortMode.name());
                root.add("accounts", arr);
                AtomicFileIO.writeUtf8Atomically(p, w -> PRETTY_GSON.toJson(root, w));
                return true;
            } catch (IOException e) {
                Strange.LOGGER.warn("Ошибка сохранения", e);
                return false;
            }
        }

        private static void readAccounts(JsonArray arr, List<AltAccount> active,
                                         List<AltAccount> deleted, Path src) {
            Set<String> an = new HashSet<>(), dn = new HashSet<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonElement el = arr.get(i);
                if (!el.isJsonObject()) continue;
                try {
                    JsonObject obj   = el.getAsJsonObject();
                    AltAccount acc   = readAccount(obj);
                    if (acc == null) continue;
                    boolean   isDel  = readBool(obj, "deleted");
                    String    norm   = norm(acc.name);
                    if (isDel) {
                        if (an.contains(norm) || !dn.add(norm)) continue;
                        deleted.add(acc);
                    } else {
                        if (!an.add(norm)) continue;
                        if (dn.remove(norm)) removeByName(deleted, acc.name);
                        active.add(acc);
                    }
                } catch (Exception ignored) {}
            }
        }

        private static AltAccount readAccount(JsonObject obj) {
            if (!obj.has("name") || !obj.get("name").isJsonPrimitive()) return null;
            String name = san(obj.get("name").getAsString());
            if (name == null) return null;
            String date = obj.has("date") && obj.get("date").isJsonPrimitive()
                    && obj.get("date").getAsJsonPrimitive().isString()
                    ? obj.get("date").getAsString().trim() : "";
            return new AltAccount(name, date, readCreatedAt(obj),
                    readBool(obj, "pinned"),
                    readInt(obj, "timesUsed"),
                    readLong(obj, "lastUsedAt"));
        }

        private static String sanSel(List<AltAccount> active, String sel) {
            if (sel == null || sel.isBlank()) return null;
            for (AltAccount a : active) if (a.name.equalsIgnoreCase(sel)) return a.name;
            return null;
        }

        private static AltSortMode readSortMode(JsonObject root) {
            if (root == null || !root.has("sortMode")
                    || !root.get("sortMode").isJsonPrimitive()) return null;
            try { return AltSortMode.valueOf(root.get("sortMode").getAsString()); }
            catch (IllegalArgumentException e) { return null; }
        }

        private static void appendAccounts(JsonArray arr,
                                           List<AltAccount> list, boolean del) {
            for (AltAccount a : list) {
                String s = san(a.name);
                if (s == null) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("name",       s);
                obj.addProperty("date",       a.date);
                obj.addProperty("createdAt",  a.createdAt);
                obj.addProperty("pinned",     a.pinned);
                obj.addProperty("timesUsed",  a.timesUsed);
                obj.addProperty("lastUsedAt", a.lastUsedAt);
                obj.addProperty("deleted",    del);
                arr.add(obj);
            }
        }

        private static boolean readBool(JsonObject o, String k) {
            return o != null && o.has(k) && o.get(k).isJsonPrimitive()
                    && o.get(k).getAsJsonPrimitive().isBoolean()
                    && o.get(k).getAsBoolean();
        }
        private static int readInt(JsonObject o, String k) {
            if (o == null || !o.has(k)) return 0;
            var e = o.get(k);
            return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()
                    ? e.getAsInt() : 0;
        }
        private static long readLong(JsonObject o, String k) {
            if (o == null || !o.has(k)) return 0L;
            var e = o.get(k);
            return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()
                    ? e.getAsLong() : 0L;
        }
        private static long readCreatedAt(JsonObject o) {
            long fb = System.currentTimeMillis();
            if (o == null || !o.has("createdAt")
                    || !o.get("createdAt").isJsonPrimitive()) return fb;
            var p = o.get("createdAt").getAsJsonPrimitive();
            if (!p.isNumber()) return fb;
            long v = p.getAsLong();
            return v > 0L ? v : fb;
        }
        private static String san(String s) {
            if (s == null) return null;
            String t = s.trim();
            return t.isBlank() ? null : t;
        }
        private static String norm(String s) {
            String n = san(s);
            return n == null ? "" : n.toLowerCase(Locale.ROOT);
        }
        private static void removeByName(List<AltAccount> list, String name) {
            String n = norm(name);
            for (int i = 0; i < list.size(); i++)
                if (norm(list.get(i).name).equals(n)) { list.remove(i); return; }
        }

        public record LoadedAccounts(List<AltAccount> active, List<AltAccount> deleted,
                                     String selectedActiveName, AltSortMode sortMode) {}
    }

    public static final class AltAccountController {
        private final AltAccountStore  accountStore;
        private final List<AltAccount> activeAccounts  = new ArrayList<>();
        private final List<AltAccount> deletedAccounts = new ArrayList<>();
        private final List<AltAccount> activeView      =
                Collections.unmodifiableList(activeAccounts);
        private final List<AltAccount> deletedView     =
                Collections.unmodifiableList(deletedAccounts);
        private String selectedActiveName;

        public AltAccountController(AltAccountStore store) { this.accountStore = store; }

        public List<AltAccount> activeAccounts()  { return activeView;  }
        public List<AltAccount> deletedAccounts() { return deletedView; }
        public String selectedActiveName()        { return selectedActiveName; }

        public void setSelectedActiveName(String name) {
            selectedActiveName = sanitize(name);
        }

        public AltSortMode load(AltSortMode fallback) {
            AltAccountStore.LoadedAccounts loaded = accountStore.load();
            activeAccounts.clear();  activeAccounts.addAll(loaded.active());
            deletedAccounts.clear(); deletedAccounts.addAll(loaded.deleted());
            selectedActiveName = loaded.selectedActiveName();
            AltSortMode resolved = loaded.sortMode() != null ? loaded.sortMode() : fallback;
            sortActive(resolved);
            reconcile();
            return resolved;
        }

        public boolean save(AltSortMode sortMode) {
            reconcile();
            return accountStore.save(
                    activeAccounts, deletedAccounts, selectedActiveName, sortMode);
        }

        public Snapshot snapshot() {
            return new Snapshot(copy(activeAccounts), copy(deletedAccounts),
                    selectedActiveName);
        }

        public void restore(Snapshot snap) {
            if (snap == null) return;
            activeAccounts.clear();
            activeAccounts.addAll(copy(snap.activeAccounts()));
            deletedAccounts.clear();
            deletedAccounts.addAll(copy(snap.deletedAccounts()));
            selectedActiveName = snap.selectedActiveName();
            reconcile();
        }

        public void sortActive(AltSortMode sm) {
            activeAccounts.sort(
                    Comparator.comparing((AltAccount a) -> !a.pinned)
                            .thenComparing(sm.comparator()));
        }

        public boolean containsName(String name) {
            return idx(activeAccounts, name) != -1 || idx(deletedAccounts, name) != -1;
        }

        public AltAccount createAccount(String name, AltSortMode sm) {
            AltAccount a = AltAccount.create(name);
            activeAccounts.add(a);
            sortActive(sm);
            return a;
        }

        public AltAccount moveToDeleted(int i) {
            if (i < 0 || i >= activeAccounts.size()) return null;
            AltAccount r = activeAccounts.remove(i);
            deletedAccounts.add(0, r);
            reconcile();
            return r;
        }

        public AltAccount restoreDeleted(int i, AltSortMode sm) {
            if (i < 0 || i >= deletedAccounts.size()) return null;
            AltAccount r = deletedAccounts.remove(i);
            activeAccounts.add(r);
            sortActive(sm);
            return r;
        }

        public String deleteDeletedPermanently(int i) {
            if (i < 0 || i >= deletedAccounts.size()) return null;
            return deletedAccounts.remove(i).name;
        }

        public int findActiveIndex(String name) { return idx(activeAccounts, name); }

        private static int idx(List<AltAccount> list, String name) {
            if (name == null) return -1;
            for (int i = 0; i < list.size(); i++)
                if (list.get(i).name.equalsIgnoreCase(name)) return i;
            return -1;
        }

        private String sanitize(String name) {
            int i = idx(activeAccounts, name);
            return i == -1 ? null : activeAccounts.get(i).name;
        }

        private void reconcile() {
            selectedActiveName = sanitize(selectedActiveName);
        }

        private static List<AltAccount> copy(List<AltAccount> src) {
            List<AltAccount> out = new ArrayList<>(src.size());
            for (AltAccount a : src) out.add(a.copy());
            return out;
        }

        public record Snapshot(List<AltAccount> activeAccounts,
                               List<AltAccount> deletedAccounts,
                               String selectedActiveName) {}
    }

    public static final class AltSessionService {
        private static final Pattern PATTERN =
                Pattern.compile("^[A-Za-z0-9_]{3,16}$");
        private static final String DEFAULT = "Player";

        public enum SwitchResult { SUCCESS, INVALID_NAME, IN_GAME, FAILED }
        public record SessionSnapshot(Session session, String username) {}

        public SwitchResult switchToOfflineProfile(MinecraftClient client, String name) {
            if (client == null)       return SwitchResult.FAILED;
            if (client.world != null) return SwitchResult.IN_GAME;
            String n = normalizeName(name);
            if (n == null)            return SwitchResult.INVALID_NAME;
            Session s = new Session(n,
                    UUID.nameUUIDFromBytes(
                            ("OfflinePlayer:" + n).getBytes(StandardCharsets.UTF_8)),
                    "0", Optional.empty(), Optional.empty(),
                    Session.AccountType.LEGACY);
            return apply(client, s, n) ? SwitchResult.SUCCESS : SwitchResult.FAILED;
        }

        public SessionSnapshot captureSession(MinecraftClient client) {
            if (client == null) return new SessionSnapshot(null, DEFAULT);
            return new SessionSnapshot(client.getSession(), getCurrentName(client));
        }

        public boolean restoreSession(MinecraftClient client, SessionSnapshot snap) {
            if (snap == null || snap.session() == null) return false;
            return apply(client, snap.session(), snap.username());
        }

        public boolean isCurrentProfile(MinecraftClient client, String name) {
            String n = normalizeName(name);
            return n != null && n.equalsIgnoreCase(getCurrentName(client));
        }

        public String getCurrentName(MinecraftClient client) {
            if (client == null || client.getSession() == null) return DEFAULT;
            String u = client.getSession().getUsername();
            return u == null || u.isBlank() ? DEFAULT : u;
        }

        public String normalizeName(String name) {
            if (name == null) return null;
            String n = name.trim();
            return PATTERN.matcher(n).matches() ? n : null;
        }

        private boolean apply(MinecraftClient client, Session session, String expected) {
            if (client == null || session == null) return false;
            if (!(client instanceof MinecraftClientAccessor acc)) {
                Strange.LOGGER.warn("Accessor недоступен");
                return false;
            }
            acc.setSession(session);
            ServerUtil.invalidateCache();
            return client.getSession() != null;
        }
    }
}