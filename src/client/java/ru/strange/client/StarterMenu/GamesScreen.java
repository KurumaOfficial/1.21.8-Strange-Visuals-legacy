package ru.strange.client.StarterMenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.List;

public class GamesScreen extends Screen {

    private final Screen parent;
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private float alpha;

    public GamesScreen(Screen parent) {
        super(Text.literal("Secret Games"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        alpha = 0f;
        clearChildren();
        buttons.clear();

        int bw = 160, bh = 40, gap = 12;
        int cx = width / 2;
        int startY = height / 2 - bh - gap / 2;

        ButtonWidget tetrisBtn = ButtonWidget.builder(
                Text.literal("Тетрис"),
                b -> client.setScreen(new TetrisScreen(this))
        ).dimensions(cx - bw / 2, startY, bw, bh).build();

        ButtonWidget pacmanBtn = ButtonWidget.builder(
                Text.literal("Пакман"),
                b -> client.setScreen(new PacmanScreen(this))
        ).dimensions(cx - bw / 2, startY + bh + gap, bw, bh).build();

        ButtonWidget backBtn = ButtonWidget.builder(
                Text.literal("← Назад"),
                b -> close()
        ).dimensions(cx - 50, startY + (bh + gap) * 2 + 8, 100, 20).build();

        tetrisBtn.setAlpha(0f);
        pacmanBtn.setAlpha(0f);
        backBtn.setAlpha(0f);

        buttons.add(addDrawableChild(tetrisBtn));
        buttons.add(addDrawableChild(pacmanBtn));
        buttons.add(addDrawableChild(backBtn));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MenuBackgroundManager.renderPanoramaBackground(ctx, width, height, 1f);

        alpha = Math.min(1f, alpha + 0.05f);
        for (ButtonWidget b : buttons) b.setAlpha(alpha);

        super.render(ctx, mouseX, mouseY, delta);

        String title = "СЕКРЕТНЫЕ ИГРЫ";
        float tw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, title, 14);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, title,
                (width - tw) / 2f, height / 2f - 70, 14, 0xFF7B2FFF);

        String sub = "Введите промокод BoxingGames в чате (.promo activate BoxingGames)";
        float sw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, sub, 5);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, sub,
                (width - sw) / 2f, height / 2f + 80, 5, 0xFF555555);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (keyCode == GLFW.GLFW_KEY_1 || keyCode == GLFW.GLFW_KEY_KP_1) { client.setScreen(new TetrisScreen(this)); return true; }
        if (keyCode == GLFW.GLFW_KEY_2 || keyCode == GLFW.GLFW_KEY_KP_2) { client.setScreen(new PacmanScreen(this)); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent != null ? parent : new TitleScreen());
    }

    @Override public boolean shouldPause() { return false; }
}
