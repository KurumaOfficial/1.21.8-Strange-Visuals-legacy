package ru.strange.client.StarterMenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.utils.other.CapeUtil;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CapeScreen extends Screen {

    private final Screen parent;
    private final List<String> capeFiles = new ArrayList<>();
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private float alpha;
    private int selectedIndex = -1;

    private static final int COLS = 4;
    private static final int SLOT_W = 64;
    private static final int SLOT_H = 80;
    private static final int GAP = 8;
    private static final int HEADER_H = 30;

    public CapeScreen(Screen parent) {
        super(Text.literal("Capes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        alpha = 0f;
        clearChildren();
        buttons.clear();
        capeFiles.clear();
        for (String f : CapeUtil.listCapeFiles()) {
            if (f.toLowerCase().endsWith(".png")) capeFiles.add(f);
        }
        selectedIndex = -1;

        int cx = width / 2;
        int gridW = COLS * (SLOT_W + GAP) - GAP;
        int gridX = cx - gridW / 2;
        int gridY = HEADER_H + 12;

        int rows = Math.max(1, (capeFiles.size() + COLS - 1) / COLS);
        int loadBtnY = gridY + rows * (SLOT_H + GAP) + 12;

        ButtonWidget loadBtn = ButtonWidget.builder(
                Text.literal("Загрузить плащ"),
                b -> { CapeUtil.uiPickAndApplyCape(); }
        ).dimensions(cx - 100, loadBtnY, 120, 20).build();
        loadBtn.setAlpha(0f);
        buttons.add(addDrawableChild(loadBtn));

        ButtonWidget openBtn = ButtonWidget.builder(
                Text.literal("Папка плащей"),
                b -> { CapeUtil.openCapeDirectory(); }
        ).dimensions(cx + 24, loadBtnY, 120, 20).build();
        openBtn.setAlpha(0f);
        buttons.add(addDrawableChild(openBtn));

        ButtonWidget resetBtn = ButtonWidget.builder(
                Text.literal("Сбросить плащ"),
                b -> { CapeUtil.uiResetCape(); close(); }
        ).dimensions(cx - 50, loadBtnY + 26, 100, 20).build();
        resetBtn.setAlpha(0f);
        buttons.add(addDrawableChild(resetBtn));

        ButtonWidget backBtn = ButtonWidget.builder(
                Text.literal("← Назад"),
                b -> close()
        ).dimensions(cx - 50, height - 30, 100, 20).build();
        backBtn.setAlpha(0f);
        buttons.add(addDrawableChild(backBtn));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Screen bg = parent;
        if (bg instanceof StarterMenuScreen) {
            ((StarterMenuScreen) bg).render(ctx, -1, -1, delta);
        } else {
            MenuBackgroundManager.renderPanoramaBackground(ctx, width, height, 1f);
        }
        RenderUtil.Round.draw(ctx, 0, 0, width, height, 0,
            RenderUtil.ColorUtil.replAlpha(0x000000, (int)(alpha * 180)));

        alpha = Math.min(1f, alpha + 0.05f);
        for (ButtonWidget b : buttons) b.setAlpha(alpha);

        super.render(ctx, mouseX, mouseY, delta);

        String title = "Плащи";
        float tw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, title, 14);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, title,
            (width - tw) / 2f, 10, 14,
            RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(alpha * 220)));

        int cx = width / 2;
        int gridW = COLS * (SLOT_W + GAP) - GAP;
        int gridX = cx - gridW / 2;
        int gridY = HEADER_H + 12;

        for (int i = 0; i < capeFiles.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            float sx = gridX + col * (SLOT_W + GAP);
            float sy = gridY + row * (SLOT_H + GAP);

            boolean hovered = mouseX >= sx && mouseX <= sx + SLOT_W
                && mouseY >= sy && mouseY <= sy + SLOT_H;

            int slotBg = RenderUtil.ColorUtil.replAlpha(
                selectedIndex == i ? RenderUtil.ColorUtil.getMainColor(1, 1) : RenderUtil.ColorUtil.getBackGroundColor(1, 1),
                (int)(alpha * (selectedIndex == i ? 160 : 100)));
            int slotBorder = RenderUtil.ColorUtil.replAlpha(
                hovered ? RenderUtil.ColorUtil.getMainColor(1, 1) : RenderUtil.ColorUtil.getTextColor(1, 1),
                (int)(alpha * (hovered ? 140 : 40)));

            RenderUtil.Round.draw(ctx, sx, sy, SLOT_W, SLOT_H, 4f, slotBg);
            RenderUtil.Border.draw(ctx, sx, sy, SLOT_W, SLOT_H, 4f, 1f, slotBorder);

            String capePath = CapeUtil.resolveCapePath(capeFiles.get(i));
            Identifier capeTex = CapeUtil.loadAndCacheCapeTexture(UUID.nameUUIDFromBytes(capePath.getBytes()), capePath);
            if (capeTex != null) {
                RenderUtil.Image.draw(ctx, capeTex,
                    sx + 8f, sy + 4f, SLOT_W - 16f, SLOT_W - 16f,
                    RenderUtil.ColorUtil.replAlpha(0xFFFFFF, (int)(alpha * 220)));
            }

            String name = capeFiles.get(i);
            if (name.toLowerCase().endsWith(".png")) name = name.substring(0, name.length() - 4);
            float nw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, name, 4);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, name,
                sx + (SLOT_W - nw) / 2f, sy + SLOT_H - 10f, 4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(alpha * 180)));
        }

        if (capeFiles.isEmpty()) {
            String empty = "Нет плащей в папке";
            float ew = FontDraw.getWidth(FontDraw.FontType.MEDIUM, empty, 6);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, empty,
                (width - ew) / 2f, gridY + 20f, 6,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(alpha * 100)));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = width / 2;
            int gridW = COLS * (SLOT_W + GAP) - GAP;
            int gridX = cx - gridW / 2;
            int gridY = HEADER_H + 12;

            for (int i = 0; i < capeFiles.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                float sx = gridX + col * (SLOT_W + GAP);
                float sy = gridY + row * (SLOT_H + GAP);

                if (mouseX >= sx && mouseX <= sx + SLOT_W
                    && mouseY >= sy && mouseY <= sy + SLOT_H) {
                    selectedIndex = i;
                    CapeUtil.useCapeFromFolder(capeFiles.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent != null ? parent : new TitleScreen());
    }

    @Override
    public boolean shouldPause() { return false; }
}
