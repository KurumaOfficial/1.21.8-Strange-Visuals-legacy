package ru.strange.client.StarterMenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.Random;

public class PacmanScreen extends Screen {

    private static final int COLS = 19;
    private static final int ROWS = 18;
    private static final int CELL = 20;

    // 0=empty 1=wall 2=dot 3=power 4=ghostHouse
    private static final int[][] MAZE = {
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
        {1,2,2,2,2,2,2,2,2,1,2,2,2,2,2,2,2,2,1},
        {1,2,1,1,2,1,1,1,2,1,2,1,1,1,2,1,1,2,1},
        {1,3,1,1,2,1,1,1,2,1,2,1,1,1,2,1,1,3,1},
        {1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1},
        {1,2,1,1,2,1,2,1,1,1,1,1,2,1,2,1,1,2,1},
        {1,2,2,2,2,1,2,2,2,1,2,2,2,1,2,2,2,2,1},
        {1,1,1,1,2,1,1,1,2,1,2,1,1,1,2,1,1,1,1},
        {0,0,0,1,2,1,2,2,2,4,2,2,2,1,2,1,0,0,0},
        {1,1,1,1,2,1,2,1,1,1,1,1,2,1,2,1,1,1,1},
        {1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1},
        {1,2,1,1,2,1,1,1,2,1,2,1,1,1,2,1,1,2,1},
        {1,2,2,2,2,1,2,2,2,1,2,2,2,1,2,2,2,2,1},
        {1,1,1,2,2,1,2,1,1,1,1,1,2,1,2,2,1,1,1},
        {1,2,2,2,2,2,2,2,2,1,2,2,2,2,2,2,2,2,1},
        {1,2,1,1,1,1,1,1,2,1,2,1,1,1,1,1,1,2,1},
        {1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1},
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
    };

    // Pacman state
    private float pacX, pacY;
    private int pacDir; // 0=right,1=left,2=up,3=down
    private int nextDir;
    private int pacAnim;

    // Ghost state
    private static class Ghost {
        float x, y;
        int dir;
        int mode; // 0=scatter,1=chase,2=frightened
        int frightTimer;
    }
    private final Ghost[] ghosts = new Ghost[4];
    private static final int[] GHOST_COLORS = {0xFFFF0000, 0xFFFFB8FF, 0xFF00FFFF, 0xFFFFB852};

    private int[][] dots;
    private int score, lives, level;
    private int totalDots;
    private boolean gameOver, paused, won;
    private long lastGhostRelease;
    private int ghostsReleased;

    private long lastTick;
    private float moveTimer, moveInterval = 0.15f;
    private final Random random = new Random();

    private final Screen parent;

    public PacmanScreen(Screen parent) {
        super(Text.literal("Pacman"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        startGame();
    }

    private void startGame() {
        dots = new int[ROWS][COLS];
        totalDots = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                dots[r][c] = MAZE[r][c];
                if (MAZE[r][c] == 2 || MAZE[r][c] == 3) totalDots++;
            }

        pacX = 9f; pacY = 10f;
        pacDir = 0; nextDir = 0;
        pacAnim = 0;
        score = 0; lives = 3; level = 1;
        gameOver = false; paused = false; won = false;
        moveInterval = 0.15f;

        for (int i = 0; i < 4; i++) {
            ghosts[i] = new Ghost();
            ghosts[i].x = 8 + i;
            ghosts[i].y = 8;
            ghosts[i].dir = 0;
            ghosts[i].mode = 0;
            ghosts[i].frightTimer = 0;
        }
        ghostsReleased = 0;
        lastGhostRelease = System.nanoTime();
        moveTimer = 0;
    }

    private void winLevel() {
        won = true;
        score += 1000;
    }

    private void loseLife() {
        lives--;
        if (lives <= 0) { gameOver = true; return; }
        pacX = 9f; pacY = 10f;
        pacDir = 0; nextDir = 0;
        for (Ghost g : ghosts) { g.x = 8 + random.nextInt(3); g.y = 8; g.dir = 0; g.mode = 0; g.frightTimer = 0; }
        ghostsReleased = 0;
        lastGhostRelease = System.nanoTime();
        moveTimer = 0;
    }

    private boolean isWall(float x, float y) {
        int cx = Math.round(x), cy = Math.round(y);
        if (cx < 0 || cx >= COLS || cy < 0 || cy >= ROWS) return true;
        return MAZE[cy][cx] == 1;
    }

    private boolean canMove(float x, float y, int dir) {
        float nx = x, ny = y;
        switch (dir) {
            case 0 -> nx += 1;
            case 1 -> nx -= 1;
            case 2 -> ny -= 1;
            case 3 -> ny += 1;
        }
        return !isWall(nx, ny);
    }

    private void gameTick() {
        if (gameOver || paused || won) return;
        long now = System.nanoTime();
        float dt = (now - lastTick) / 1_000_000_000f;
        lastTick = now;
        moveTimer += dt;

        if (moveTimer >= moveInterval) {
            moveTimer -= moveInterval;

            // Pacman movement
            if (canMove(pacX, pacY, nextDir)) pacDir = nextDir;
            if (canMove(pacX, pacY, pacDir)) {
                switch (pacDir) {
                    case 0 -> pacX += 1;
                    case 1 -> pacX -= 1;
                    case 2 -> pacY -= 1;
                    case 3 -> pacY += 1;
                }
                pacAnim++;
                // Eat dot
                int cx = Math.round(pacX), cy = Math.round(pacY);
                if (cy >= 0 && cy < ROWS && cx >= 0 && cx < COLS) {
                    if (dots[cy][cx] == 2) { dots[cy][cx] = 0; score += 10; totalDots--; }
                    if (dots[cy][cx] == 3) {
                        dots[cy][cx] = 0; score += 50;
                        for (Ghost g : ghosts) { g.mode = 2; g.frightTimer = 240; }
                        totalDots--;
                    }
                }
            }

            // Ghost AI
            for (int i = 0; i < 4; i++) {
                Ghost g = ghosts[i];
                if (g.frightTimer > 0) g.frightTimer--;
                if (g.frightTimer == 0 && g.mode == 2) g.mode = 1;

                if (random.nextFloat() < 0.1f || !canMove(g.x, g.y, g.dir)) {
                    int[] dirs = {0, 1, 2, 3};
                    int best = g.dir;
                    float bestDist = Float.MAX_VALUE;
                    for (int d : dirs) {
                        if (d == (g.dir ^ 1)) continue;
                        if (!canMove(g.x, g.y, d)) continue;
                        float nx = g.x, ny = g.y;
                        switch (d) { case 0 -> nx++; case 1 -> nx--; case 2 -> ny--; case 3 -> ny++; }
                        float dx = nx - pacX, dy = ny - pacY;
                        float dist = dx * dx + dy * dy;
                        if (g.mode == 0) dist = -dist;
                        if (g.mode == 2) dist = -dist;
                        if (dist < bestDist) { bestDist = dist; best = d; }
                    }
                    g.dir = best;
                }
                switch (g.dir) {
                    case 0 -> g.x += 1;
                    case 1 -> g.x -= 1;
                    case 2 -> g.y -= 1;
                    case 3 -> g.y += 1;
                }

                // Check collision
                float dx = g.x - pacX, dy = g.y - pacY;
                if (dx * dx + dy * dy < 1.5f) {
                    if (g.mode == 2) {
                        g.x = 9; g.y = 8; g.mode = 0; g.frightTimer = 0;
                        score += 200;
                    } else {
                        loseLife();
                        return;
                    }
                }
            }

            if (totalDots <= 0) winLevel();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        gameTick();

        int mw = COLS * CELL, mh = ROWS * CELL;
        int mx = (width - mw) / 2, my = (height - mh) / 2;
        int bg = 0xFF000000;

        ctx.fill(0, 0, width, height, bg);

        // Draw maze
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int x = mx + c * CELL, y = my + r * CELL;
                if (MAZE[r][c] == 1) {
                    ctx.fill(x, y, x + CELL, y + CELL, 0xFF2121DE);
                } else if (dots[r][c] == 2) {
                    ctx.fill(x + CELL / 2 - 2, y + CELL / 2 - 2, x + CELL / 2 + 2, y + CELL / 2 + 2, 0xFFFFB8AE);
                } else if (dots[r][c] == 3) {
                    ctx.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, 0xFFFFB8AE);
                }
            }

        // Draw ghosts
        for (int i = 0; i < 4; i++) {
            Ghost g = ghosts[i];
            int gx = mx + Math.round(g.x) * CELL + 1;
            int gy = my + Math.round(g.y) * CELL + 1;
            int color = g.mode == 2 ? 0xFF2121FF : GHOST_COLORS[i];
            RenderUtil.Round.draw(ctx, gx, gy, CELL - 2, CELL - 2, 4, color);
        }

        // Draw pacman
        int px = mx + Math.round(pacX) * CELL + 1;
        int py = my + Math.round(pacY) * CELL + 1;
        RenderUtil.Round.draw(ctx, px, py, CELL - 2, CELL - 2, 8, 0xFFFFFF00);

        // HUD
        int hudX = mx + mw + 16;
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Счет: " + score, hudX, my, 6, 0xFFFFB8AE);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Жизни: " + lives, hudX, my + 20, 6, 0xFFFFB8AE);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Уровень: " + level, hudX, my + 40, 6, 0xFFFFB8AE);

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Управление:", hudX, my + 80, 6, 0xFF888888);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "←↑↓→ - движение", hudX, my + 96, 5, 0xFF666666);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "P - пауза", hudX, my + 110, 5, 0xFF666666);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "ESC - назад", hudX, my + 124, 5, 0xFF666666);

        if (won) {
            ctx.fill(0, 0, width, height, 0x88000000);
            String msg = "УРОВЕНЬ ПРОЙДЕН!";
            float mw2 = FontDraw.getWidth(FontDraw.FontType.MEDIUM, msg, 12);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, msg, (width - mw2) / 2f, height / 2f - 10, 12, 0xFF00FF00);
        } else if (gameOver) {
            ctx.fill(0, 0, width, height, 0x88000000);
            String msg = "ИГРА ОКОНЧЕНА";
            float mw2 = FontDraw.getWidth(FontDraw.FontType.MEDIUM, msg, 14);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, msg, (width - mw2) / 2f, height / 2f - 16, 14, 0xFFFF4444);
            String rs = "Нажмите ENTER для рестарта";
            float rw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, rs, 6);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, rs, (width - rw) / 2f, height / 2f + 14, 6, 0xFFAAAAAA);
        } else if (paused) {
            ctx.fill(0, 0, width, height, 0x88000000);
            String msg = "ПАУЗА";
            float mw2 = FontDraw.getWidth(FontDraw.FontType.MEDIUM, msg, 14);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, msg, (width - mw2) / 2f, height / 2f, 14, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (gameOver) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) startGame();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_P) { paused = !paused; return true; }
        if (paused) return true;

        switch (keyCode) {
            case GLFW.GLFW_KEY_RIGHT -> nextDir = 0;
            case GLFW.GLFW_KEY_LEFT -> nextDir = 1;
            case GLFW.GLFW_KEY_UP -> nextDir = 2;
            case GLFW.GLFW_KEY_DOWN -> nextDir = 3;
        }
        return true;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent != null ? parent : new TitleScreen());
    }

    @Override public boolean shouldPause() { return false; }
}
