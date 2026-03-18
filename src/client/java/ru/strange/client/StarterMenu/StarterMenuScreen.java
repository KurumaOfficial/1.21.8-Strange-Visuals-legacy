package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.utils.render.RenderUtil;
import java.awt.Color;
import java.util.Random;

public class StarterMenuScreen extends Screen {

    private static final int BTN_W = 190;
    private static final int BTN_H = 34;
    private static final int CR    = 8;
    private static final int GAP   = 8;

    private static final int ICON_H  = 48;
    private static final int TEXT_H  = 32;
    private static final int BTNS_H  = BTN_H * 4 + GAP * 3;
    private static final int TOTAL_H = ICON_H + 14 + TEXT_H + BTNS_H;

    private int cx, cy;
    private float alpha = 0f;
    private float time  = 0f;
    private final float[] btnHover = new float[5];

    private static final int WAVE_COUNT = 4;
    private final float[] waveOffset = new float[WAVE_COUNT];
    private final float[] waveSpeed  = new float[WAVE_COUNT];
    private final float[] waveAmp    = new float[WAVE_COUNT];
    private final int[]   waveY      = new int[WAVE_COUNT];

    private static final int ORBS = 5;
    private final float[] orbX  = new float[ORBS];
    private final float[] orbY  = new float[ORBS];
    private final float[] orbVX = new float[ORBS];
    private final float[] orbVY = new float[ORBS];
    private final int[]   orbR  = new int[ORBS];
    private boolean orbsInit = false;

    public StarterMenuScreen() {
        super(Text.literal("Strange Visuals"));
    }

    private int blockTop() { return cy - TOTAL_H / 2; }
    private int getBy()    { return blockTop() + ICON_H + 14 + TEXT_H; }

    @Override
    protected void init() {
        cx = width  / 2;
        cy = height / 2;

        for (int i = 0; i < WAVE_COUNT; i++) {
            waveY[i]      = (int)(height * (i + 1f) / (WAVE_COUNT + 1f));
            waveOffset[i] = i * 1.8f;
            waveSpeed[i]  = 0.005f + i * 0.002f;
            waveAmp[i]    = 10 + i * 5;
        }

        if (!orbsInit) {
            Random rng = new Random(7);
            for (int i = 0; i < ORBS; i++) {
                orbX[i]  = rng.nextFloat() * width;
                orbY[i]  = rng.nextFloat() * height;
                orbVX[i] = (rng.nextFloat() - 0.5f) * 0.3f;
                orbVY[i] = (rng.nextFloat() - 0.5f) * 0.2f;
                orbR[i]  = 60 + rng.nextInt(100);
            }
            orbsInit = true;
        }

        int bx     = cx - BTN_W / 2;
        int by     = getBy();
        int smallW = (BTN_W - 6) / 2;

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("Single Player"),
                btn -> client.setScreen(new SelectWorldScreen(this))
        ).dimensions(bx, by, BTN_W, BTN_H).build()));

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("Multi Player"),
                btn -> client.setScreen(new MultiplayerScreen(this))
        ).dimensions(bx, by + (BTN_H + GAP), BTN_W, BTN_H).build()));

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("Alt Manager"),
                btn -> client.setScreen(new AltManagerScreen(this))
        ).dimensions(bx, by + (BTN_H + GAP) * 2, BTN_W, BTN_H).build()));

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("Quit"),
                btn -> MinecraftClient.getInstance().scheduleStop()
        ).dimensions(bx, by + (BTN_H + GAP) * 3, smallW, BTN_H).build()));

        addDrawableChild(makeInvisible(ButtonWidget.builder(
                Text.literal("Options"),
                btn -> client.setScreen(new OptionsScreen(this, client.options))
        ).dimensions(bx + smallW + 6, by + (BTN_H + GAP) * 3, smallW, BTN_H).build()));
    }

    private ButtonWidget makeInvisible(ButtonWidget btn) {
        btn.setAlpha(0f);
        return btn;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (alpha < 1f) alpha = Math.min(1f, alpha + 0.05f);
        time += 0.016f;
        float a = alpha;

        int top    = blockTop();
        int bx     = cx - BTN_W / 2;
        int by     = getBy();
        int smallW = (BTN_W - 6) / 2;

        // Фон
        ctx.fill(0, 0, width, height, 0xFF000000);

        // Радиальный градиент
        for (int r = 300; r > 0; r -= 8) {
            float fade = 1f - r / 300f;
            ctx.fill(cx - r, cy - r, cx + r, cy + r,
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
            ctx.fill(0, yy, width, yy+1, new Color(255,255,255,la).getRGB());
        for (int xx = 0; xx < width; xx += 40)
            ctx.fill(xx, 0, xx+1, height, new Color(255,255,255,la).getRGB());

        // Волны
        for (int i = 0; i < WAVE_COUNT; i++) {
            waveOffset[i] += waveSpeed[i];
            int baseY = waveY[i];
            int wa = (int)(7 * a * (1f - Math.abs(i - WAVE_COUNT / 2f) / WAVE_COUNT));
            for (int x = 0; x < width - 2; x += 2) {
                int y1 = baseY + (int)(Math.sin(x * 0.020f + waveOffset[i]) * waveAmp[i]);
                int y2 = baseY + (int)(Math.sin((x+2) * 0.020f + waveOffset[i]) * waveAmp[i]);
                ctx.fill(x, Math.min(y1,y2), x+2, Math.max(y1,y2)+1,
                        new Color(255,255,255,wa).getRGB());
            }
        }

        // Пульс
        float pulse = (float)(Math.sin(time * 1.0f) * 0.5f + 0.5f);
        for (int r = 200; r > 0; r -= 10) {
            float fade = 1f - r / 200f;
            ctx.fill(cx-r, cy-r, cx+r, cy+r,
                    new Color(12,12,14,(int)(8*fade*pulse*a)).getRGB());
        }

        // Иконка
        int iconSize = ICON_H;
        int iconX    = cx - iconSize / 2;
        int iconY    = top;
        float iconPulse = (float)(Math.sin(time * 0.8f) * 0.5f + 0.5f);
        for (int r = 34; r > 0; r -= 4) {
            float fade = 1f - r / 34f;
            ctx.fill(cx-r, iconY+iconSize/2-r, cx+r, iconY+iconSize/2+r,
                    new Color(255,255,255,(int)(4*fade*iconPulse*a)).getRGB());
        }
        RenderUtil.Round.draw(ctx, iconX, iconY, iconSize, iconSize, iconSize/2,
                new Color(14,14,16,(int)(235*a)));
        RenderUtil.Border.draw(ctx, iconX, iconY, iconSize, iconSize, iconSize/2, 0.5f,
                new Color(255,255,255,(int)((16+iconPulse*16)*a)));
        RenderUtil.Round.draw(ctx, cx-9, iconY+8, 18, 17, 9,
                new Color(195,195,200,(int)(215*a)));
        RenderUtil.Round.draw(ctx, cx-13, iconY+29, 26, 14, 7,
                new Color(195,195,200,(int)(215*a)));

        // Название
        int nameY = top + ICON_H + 10;
        String title = "Strange Visuals.";
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, title, cx-tw/2+1, nameY+1,
                new Color(0,0,0,(int)(80*a)).getRGB(), false);
        ctx.drawText(textRenderer, title, cx-tw/2, nameY,
                new Color(240,240,245,(int)(240*a)).getRGB(), false);

        String ver = "v1.0  ·  1.21.8";
        int vw = textRenderer.getWidth(ver);
        ctx.drawText(textRenderer, ver, cx-vw/2, nameY+13,
                new Color(75,75,80,(int)(180*a)).getRGB(), false);

        int lineW = 80;
        for (int lx = 0; lx < lineW; lx++) {
            float fade = 1f - Math.abs(lx - lineW/2f) / (lineW/2f);
            ctx.fill(cx-lineW/2+lx, nameY+26, cx-lineW/2+lx+1, nameY+27,
                    new Color(255,255,255,(int)(30*fade*a)).getRGB());
        }

        // Hover + кнопки
        updateHover(bx, by, smallW, mouseX, mouseY, delta);
        drawBtn(ctx, bx, by,                         BTN_W,  BTN_H, btnHover[0], a, "Single Player");
        drawBtn(ctx, bx, by+(BTN_H+GAP),             BTN_W,  BTN_H, btnHover[1], a, "Multi Player");
        drawBtn(ctx, bx, by+(BTN_H+GAP)*2,           BTN_W,  BTN_H, btnHover[2], a, "Alt Manager");
        drawBtn(ctx, bx,              by+(BTN_H+GAP)*3, smallW, BTN_H, btnHover[3], a, "Quit");
        drawBtn(ctx, bx+smallW+6,     by+(BTN_H+GAP)*3, smallW, BTN_H, btnHover[4], a, "Options");

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void updateHover(int bx, int by, int smallW,
                             int mouseX, int mouseY, float delta) {
        int[][] btns = {
                {bx,          by,                  BTN_W,  BTN_H},
                {bx,          by+(BTN_H+GAP),      BTN_W,  BTN_H},
                {bx,          by+(BTN_H+GAP)*2,    BTN_W,  BTN_H},
                {bx,          by+(BTN_H+GAP)*3,    smallW, BTN_H},
                {bx+smallW+6, by+(BTN_H+GAP)*3,   smallW, BTN_H}
        };
        for (int i = 0; i < btns.length; i++) {
            boolean hov = mouseX >= btns[i][0] && mouseX <= btns[i][0]+btns[i][2]
                    && mouseY >= btns[i][1] && mouseY <= btns[i][1]+btns[i][3];
            btnHover[i] = hov
                    ? Math.min(1f, btnHover[i] + delta * 0.35f)
                    : Math.max(0f, btnHover[i] - delta * 0.35f);
        }
    }

    private void drawBtn(DrawContext ctx, int x, int y, int w, int h,
                         float hover, float a, String label) {
        int bg = (int)(7 + hover * 22);
        RenderUtil.Round.draw(ctx, x, y, w, h, CR,
                new Color(bg, bg, bg, (int)(240*a)));
        RenderUtil.Border.draw(ctx, x, y, w, h, CR, 0.5f,
                new Color(255,255,255,(int)((10+hover*60)*a)));
        if (hover > 0.01f) {
            for (int dy = 0; dy < h-12; dy++) {
                float fade = 1f - Math.abs(dy-(h-12)/2f)/((h-12)/2f);
                ctx.fill(x+1, y+6+dy, x+2, y+7+dy,
                        new Color(255,255,255,(int)(hover*140*fade*a)).getRGB());
                ctx.fill(x+w-2, y+6+dy, x+w-1, y+7+dy,
                        new Color(255,255,255,(int)(hover*70*fade*a)).getRGB());
            }
        }
        int tw = textRenderer.getWidth(label);
        int tc = (int)(180 + hover * 75);
        ctx.drawText(textRenderer, label,
                x+(w-tw)/2, y+(h-textRenderer.fontHeight)/2,
                new Color(tc,tc,tc,(int)(230*a)).getRGB(), false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}