package ru.strange.client.StarterMenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.utils.render.RenderUtil;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AltManagerScreen extends Screen {

    private static final int CR      = 8;
    private static final int PANEL_W = 230;
    private static final int PANEL_H = 230;

    private static class Account {
        String name, date;
        Account(String name) {
            this.name = name;
            this.date = new SimpleDateFormat("HH:mm dd.MM.yy").format(new Date());
        }
    }

    private final Screen parent;
    private final List<Account> accounts = new ArrayList<>();
    private String currentAccount = null;
    private TextFieldWidget inputField;
    private int scrollOffset = 0;
    private String message   = "";
    private float msgTimer   = 0f;
    private float alpha      = 0f;
    private float time       = 0f;
    private int px, py;

    // Анимация появления карточек
    private float listAlpha = 0f;

    private static final int WAVE_COUNT = 4;
    private final float[] waveOffset = new float[WAVE_COUNT];
    private final float[] waveSpeed  = new float[WAVE_COUNT];
    private final float[] waveAmp    = new float[WAVE_COUNT];
    private final int[]   waveY      = new int[WAVE_COUNT];

    private static final int ORBS = 4;
    private final float[] orbX  = new float[ORBS];
    private final float[] orbY  = new float[ORBS];
    private final float[] orbVX = new float[ORBS];
    private final float[] orbVY = new float[ORBS];
    private final int[]   orbR  = new int[ORBS];
    private boolean orbsInit = false;

    // Hover для кнопок
    private float hRnd = 0, hAdd = 0, hRem = 0, hClr = 0, hBck = 0;

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        px = (width  - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        for (int i = 0; i < WAVE_COUNT; i++) {
            waveY[i]      = (int)(height * (i + 1f) / (WAVE_COUNT + 1f));
            waveOffset[i] = i * 1.8f;
            waveSpeed[i]  = 0.005f + i * 0.002f;
            waveAmp[i]    = 10 + i * 5;
        }

        if (!orbsInit) {
            java.util.Random rng = new java.util.Random(13);
            for (int i = 0; i < ORBS; i++) {
                orbX[i]  = rng.nextFloat() * width;
                orbY[i]  = rng.nextFloat() * height;
                orbVX[i] = (rng.nextFloat() - 0.5f) * 0.3f;
                orbVY[i] = (rng.nextFloat() - 0.5f) * 0.2f;
                orbR[i]  = 60 + rng.nextInt(100);
            }
            orbsInit = true;
        }

        int fieldY = py + PANEL_H - 50;
        inputField = new TextFieldWidget(textRenderer,
                px + 10, fieldY + 2, PANEL_W - 22, 16,
                Text.literal("Nickname"));
        inputField.setMaxLength(16);
        inputField.setPlaceholder(Text.literal("Enter nickname..."));
        addDrawableChild(inputField);

        int btnY = py + PANEL_H - 30;
        int bw   = 44;
        int gap  = 3;

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("Random"),
                btn -> inputField.setText(NickGenerator.generate())
        ).dimensions(px + 10, btnY, bw, 16).build()));

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("Add"),
                btn -> addAccount(inputField.getText())
        ).dimensions(px + 10 + bw + gap, btnY, 30, 16).build()));

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("Remove"),
                btn -> {
                    if (currentAccount != null) {
                        accounts.removeIf(acc -> acc.name.equals(currentAccount));
                        currentAccount = null;
                        showMsg("Removed!");
                    } else showMsg("Select first!");
                }
        ).dimensions(px + 10 + bw + gap + 30 + gap, btnY, bw, 16).build()));

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("Clear"),
                btn -> { accounts.clear(); currentAccount = null; scrollOffset = 0; showMsg("Cleared!"); }
        ).dimensions(px + 10 + bw + gap + 30 + gap + bw + gap, btnY, 34, 16).build()));

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("< Back"),
                btn -> client.setScreen(parent)
        ).dimensions(px + 10, py + PANEL_H - 10, 50, 10).build()));
    }

    private ButtonWidget makeInvisible(ButtonWidget btn) {
        btn.setAlpha(0f);
        return btn;
    }

    private void addAccount(String name) {
        name = name.trim();
        if (name.isEmpty())            { showMsg("Enter a nickname!"); return; }
        if (name.length() < 3)         { showMsg("Too short!"); return; }
        for (Account acc : accounts)
            if (acc.name.equals(name)) { showMsg("Already exists!"); return; }
        if (accounts.size() >= 50)     { showMsg("Max 50!"); return; }
        accounts.add(new Account(name));
        inputField.setText("");
        showMsg("Added: " + name);
    }

    private void showMsg(String msg) {
        this.message  = msg;
        this.msgTimer = 3f;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (alpha < 1f)     alpha     = Math.min(1f, alpha     + 0.05f);
        if (listAlpha < 1f) listAlpha = Math.min(1f, listAlpha + 0.03f);
        if (msgTimer > 0f)  msgTimer -= delta * 0.05f;
        time += 0.016f;
        float a = alpha;

        // Фон
        ctx.fill(0, 0, width, height, 0xFF000000);

        // Радиальный градиент
        for (int r = 300; r > 0; r -= 8) {
            float fade = 1f - r / 300f;
            ctx.fill(width/2 - r, height/2 - r, width/2 + r, height/2 + r,
                    new Color(10, 10, 12, (int)(12 * fade * a)).getRGB());
        }

        // Блики
        for (int i = 0; i < ORBS; i++) {
            orbX[i] += orbVX[i]; orbY[i] += orbVY[i];
            if (orbX[i] < 0 || orbX[i] > width)  orbVX[i] *= -1;
            if (orbY[i] < 0 || orbY[i] > height) orbVY[i] *= -1;
            int r = orbR[i];
            for (int ring = r; ring > 0; ring -= 8) {
                float fade = (float)Math.pow(1f - ring / (float)r, 2);
                ctx.fill((int)orbX[i]-ring, (int)orbY[i]-ring,
                        (int)orbX[i]+ring, (int)orbY[i]+ring,
                        new Color(15, 15, 18, (int)(12 * fade * a)).getRGB());
            }
        }

        // Сетка
        float gp = (float)(Math.sin(time * 0.35f) * 0.5f + 0.5f);
        int la = (int)((1 + gp * 3) * a);
        for (int yy = 0; yy < height; yy += 40)
            ctx.fill(0, yy, width, yy + 1, new Color(255, 255, 255, la).getRGB());
        for (int xx = 0; xx < width; xx += 40)
            ctx.fill(xx, 0, xx + 1, height, new Color(255, 255, 255, la).getRGB());

        // Волны
        for (int i = 0; i < WAVE_COUNT; i++) {
            waveOffset[i] += waveSpeed[i];
            int baseY = waveY[i];
            int wa = (int)(7 * a * (1f - Math.abs(i - WAVE_COUNT / 2f) / WAVE_COUNT));
            for (int x = 0; x < width - 2; x += 2) {
                int y1 = baseY + (int)(Math.sin(x * 0.020f + waveOffset[i]) * waveAmp[i]);
                int y2 = baseY + (int)(Math.sin((x+2) * 0.020f + waveOffset[i]) * waveAmp[i]);
                ctx.fill(x, Math.min(y1,y2), x+2, Math.max(y1,y2)+1,
                        new Color(255, 255, 255, wa).getRGB());
            }
        }

        // Панель — появляется снизу вверх
        float panelSlide = (float)(1f - Math.pow(1f - alpha, 3));
        int slideOff = (int)((1f - panelSlide) * 20);
        int ppx = px, ppy = py + slideOff;

        RenderUtil.Round.draw(ctx, ppx, ppy, PANEL_W, PANEL_H, CR,
                new Color(7, 7, 9, (int)(252 * a)));
        RenderUtil.Border.draw(ctx, ppx, ppy, PANEL_W, PANEL_H, CR, 0.5f,
                new Color(255, 255, 255, (int)(14 * a)));

        // Шапка
        ctx.fill(ppx + 1, ppy + 1, ppx + PANEL_W - 1, ppy + 24,
                new Color(12, 12, 14, (int)(230 * a)).getRGB());

        // Градиентная линия
        for (int lx = 0; lx < PANEL_W - 20; lx++) {
            float fade = 1f - Math.abs(lx - (PANEL_W-20)/2f) / ((PANEL_W-20)/2f);
            ctx.fill(ppx + 10 + lx, ppy + 24, ppx + 11 + lx, ppy + 25,
                    new Color(255, 255, 255, (int)(18 * fade * a)).getRGB());
        }

        // Заголовок
        String title = "Alt Manager";
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, title,
                ppx + (PANEL_W - tw) / 2 + 1, ppy + 8 + 1,
                new Color(0, 0, 0, (int)(80 * a)).getRGB(), false);
        ctx.drawText(textRenderer, title,
                ppx + (PANEL_W - tw) / 2, ppy + 8,
                new Color(240, 240, 245, (int)(230 * a)).getRGB(), false);

        // Счётчик
        ctx.drawText(textRenderer, accounts.size() + "/50",
                ppx + 10, ppy + 8,
                new Color(55, 55, 60, (int)(200 * a)).getRGB(), false);

        // Текущий
        if (currentAccount != null) {
            String cur = "@ " + currentAccount;
            ctx.drawText(textRenderer, cur,
                    ppx + PANEL_W - 10 - textRenderer.getWidth(cur), ppy + 8,
                    new Color(155, 155, 165, (int)(200 * a)).getRGB(), false);
        }

        // Список
        int listX    = ppx + 6;
        int listY    = ppy + 30;
        int listH    = PANEL_H - 88;
        int colW     = (PANEL_W - 16) / 2;
        int itemH    = 34;
        int visRows  = listH / itemH;
        int visCnt   = visRows * 2;

        ctx.enableScissor(ppx + 4, listY, ppx + PANEL_W - 4, listY + listH);

        for (int i = scrollOffset * 2;
             i < Math.min(accounts.size(), scrollOffset * 2 + visCnt); i++) {
            Account acc = accounts.get(i);
            int col = (i - scrollOffset * 2) % 2;
            int row = (i - scrollOffset * 2) / 2;
            int ix  = listX + col * (colW + 4);
            int iy  = listY + row * itemH + 2;

            // Анимация появления каждой карточки
            float cardA = Math.min(1f, listAlpha * (1f + (i % 4) * 0.1f));
            int offY    = (int)((1f - cardA) * 8);

            boolean sel    = acc.name.equals(currentAccount);
            boolean rowHov = mouseX >= ix && mouseX <= ix + colW
                    && mouseY >= iy - offY && mouseY <= iy - offY + itemH - 4;

            int cbg = sel ? 20 : (rowHov ? 16 : 9);
            RenderUtil.Round.draw(ctx, ix, iy - offY, colW, itemH - 4, CR,
                    new Color(cbg, cbg, cbg, (int)(220 * cardA * a)));
            RenderUtil.Border.draw(ctx, ix, iy - offY, colW, itemH - 4, CR, 0.5f,
                    sel ? new Color(255, 255, 255, (int)(28 * cardA * a))
                            : new Color(255, 255, 255, (int)(7 * cardA * a)));

            // Акцент выбранной
            if (sel) {
                for (int dy = 0; dy < itemH - 12; dy++) {
                    float fade = 1f - Math.abs(dy - (itemH-12)/2f) / ((itemH-12)/2f);
                    ctx.fill(ix + 1, iy - offY + 5 + dy, ix + 2, iy - offY + 6 + dy,
                            new Color(255, 255, 255, (int)(100 * fade * cardA * a)).getRGB());
                }
            }

            // Аватар
            int avSize = 18;
            int avX    = ix + 4;
            int avY    = iy - offY + (itemH - 4 - avSize) / 2;
            RenderUtil.Round.draw(ctx, avX, avY, avSize, avSize, avSize / 2,
                    new Color(14, 14, 16, (int)(220 * cardA * a)));
            RenderUtil.Border.draw(ctx, avX, avY, avSize, avSize, avSize / 2, 0.5f,
                    new Color(255, 255, 255, (int)(8 * cardA * a)));
            RenderUtil.Round.draw(ctx, avX + 4, avY + 2, 10, 8, 3,
                    new Color(sel ? 210 : 160, sel ? 210 : 160, sel ? 215 : 165,
                            (int)(210 * cardA * a)));
            RenderUtil.Round.draw(ctx, avX + 2, avY + 12, 14, 5, 2,
                    new Color(sel ? 210 : 160, sel ? 210 : 160, sel ? 215 : 165,
                            (int)(210 * cardA * a)));

            // Имя
            ctx.drawText(textRenderer, acc.name,
                    avX + avSize + 3, iy - offY + 5,
                    new Color(sel ? 245 : 195, sel ? 245 : 195, sel ? 250 : 200,
                            (int)(220 * cardA * a)).getRGB(), false);

            // Дата
            ctx.drawText(textRenderer, acc.date,
                    avX + avSize + 3, iy - offY + 15,
                    new Color(48, 48, 52, (int)(165 * cardA * a)).getRGB(), false);
        }

        ctx.disableScissor();

        // Разделитель
        for (int lx = 0; lx < PANEL_W - 20; lx++) {
            float fade = 1f - Math.abs(lx - (PANEL_W-20)/2f) / ((PANEL_W-20)/2f);
            ctx.fill(ppx + 10 + lx, ppy + PANEL_H - 56,
                    ppx + 11 + lx, ppy + PANEL_H - 55,
                    new Color(255, 255, 255, (int)(12 * fade * a)).getRGB());
        }

        // Hover кнопок
        int btnY = ppy + PANEL_H - 30;
        int bw   = 44;
        int gap  = 3;
        hRnd = updateH(hRnd, ppx+10,                            btnY, bw, 16, mouseX, mouseY, delta);
        hAdd = updateH(hAdd, ppx+10+bw+gap,                     btnY, 30, 16, mouseX, mouseY, delta);
        hRem = updateH(hRem, ppx+10+bw+gap+30+gap,              btnY, bw, 16, mouseX, mouseY, delta);
        hClr = updateH(hClr, ppx+10+bw+gap+30+gap+bw+gap,       btnY, 34, 16, mouseX, mouseY, delta);
        hBck = updateH(hBck, ppx+10,                            ppy+PANEL_H-10, 50, 10, mouseX, mouseY, delta);

        drawLabelBtn(ctx, ppx+10,                            btnY, bw, 16, hRnd, a, "Random", false);
        drawLabelBtn(ctx, ppx+10+bw+gap,                     btnY, 30, 16, hAdd, a, "Add",    false);
        drawLabelBtn(ctx, ppx+10+bw+gap+30+gap,              btnY, bw, 16, hRem, a, "Remove", true);
        drawLabelBtn(ctx, ppx+10+bw+gap+30+gap+bw+gap,       btnY, 34, 16, hClr, a, "Clear",  true);

        // Back
        ctx.drawText(textRenderer, "< Back",
                ppx + 10, ppy + PANEL_H - 9,
                new Color(
                        (int)(65 + hBck * 135),
                        (int)(65 + hBck * 135),
                        (int)(70 + hBck * 135),
                        (int)((120 + hBck * 105) * a)
                ).getRGB(), false);

        // Пустой список
        if (accounts.isEmpty()) {
            float pulse = (float)(Math.sin(time * 1.5f) * 0.5f + 0.5f);
            String empty = "No accounts. Add one!";
            int ew = textRenderer.getWidth(empty);
            ctx.drawText(textRenderer, empty,
                    ppx + (PANEL_W - ew) / 2,
                    ppy + 30 + (listH - textRenderer.fontHeight) / 2,
                    new Color(35 + (int)(pulse * 15), 35 + (int)(pulse * 15), 38 + (int)(pulse * 15),
                            (int)(180 * a)).getRGB(), false);
        }

        // Сообщение
        if (msgTimer > 0f) {
            float mf   = Math.min(msgTimer, 1f);
            int msgA   = (int)(mf * 200 * a);
            int mw     = textRenderer.getWidth(message);
            RenderUtil.Round.draw(ctx,
                    ppx + (PANEL_W - mw) / 2 - 6, ppy + PANEL_H + 2,
                    mw + 12, 14, 4,
                    new Color(10, 10, 12, (int)(180 * mf * a)));
            ctx.drawText(textRenderer, message,
                    ppx + (PANEL_W - mw) / 2, ppy + PANEL_H + 6,
                    new Color(130, 210, 135, msgA).getRGB(), false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private float updateH(float cur, int x, int y, int w, int h,
                          int mx, int my, float delta) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        return hov ? Math.min(1f, cur + delta * 0.4f)
                : Math.max(0f, cur - delta * 0.4f);
    }

    private void drawLabelBtn(DrawContext ctx, int x, int y, int w, int h,
                              float hover, float a, String label, boolean red) {
        if (red) {
            RenderUtil.Round.draw(ctx, x, y, w, h, CR,
                    new Color((int)(9 + hover * 20), 7, 7, (int)(235 * a)));
            RenderUtil.Border.draw(ctx, x, y, w, h, CR, 0.5f,
                    new Color(160, 45, 45, (int)((20 + hover * 100) * a)));
        } else {
            int bg = (int)(9 + hover * 16);
            RenderUtil.Round.draw(ctx, x, y, w, h, CR,
                    new Color(bg, bg, bg, (int)(235 * a)));
            RenderUtil.Border.draw(ctx, x, y, w, h, CR, 0.5f,
                    new Color(255, 255, 255, (int)((10 + hover * 45) * a)));
        }

        if (hover > 0.01f && !red)
            ctx.fill(x+1, y+3, x+2, y+h-3,
                    new Color(255, 255, 255, (int)(hover * 90 * a)).getRGB());

        int tw = textRenderer.getWidth(label);
        int tc = red
                ? new Color((int)(140 + hover * 115), 48, 48, (int)(225 * a)).getRGB()
                : new Color((int)(165 + hover * 90), (int)(165 + hover * 90),
                (int)(170 + hover * 85), (int)(225 * a)).getRGB();
        ctx.drawText(textRenderer, label,
                x + (w - tw) / 2, y + (h - textRenderer.fontHeight) / 2, tc, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX   = px + 6;
        int listY   = py + 30;
        int colW    = (PANEL_W - 16) / 2;
        int itemH   = 34;
        int listH   = PANEL_H - 88;
        int visRows = listH / itemH;
        int visCnt  = visRows * 2;

        for (int i = scrollOffset * 2;
             i < Math.min(accounts.size(), scrollOffset * 2 + visCnt); i++) {
            int col = (i - scrollOffset * 2) % 2;
            int row = (i - scrollOffset * 2) / 2;
            int ix  = listX + col * (colW + 4);
            int iy  = listY + row * itemH + 2;

            if (mouseX >= ix && mouseX <= ix + colW
                    && mouseY >= iy && mouseY <= iy + itemH - 4) {
                currentAccount = accounts.get(i).name.equals(currentAccount)
                        ? null : accounts.get(i).name;
                if (currentAccount != null) showMsg("Using: " + currentAccount);
                return true;
            }
        }

        if (mouseX >= px + 10 && mouseX <= px + 60
                && mouseY >= py + PANEL_H - 10 && mouseY <= py + PANEL_H) {
            client.setScreen(parent);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double ha, double verticalAmount) {
        int itemH     = 34;
        int listH     = PANEL_H - 88;
        int visRows   = listH / itemH;
        int maxScroll = Math.max(0, (int)Math.ceil(accounts.size() / 2.0) - visRows);
        scrollOffset  = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(parent); return true; }
        if (keyCode == GLFW.GLFW_KEY_ENTER && inputField.isFocused()) {
            addAccount(inputField.getText());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}