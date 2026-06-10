package ru.strange.client.StarterMenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.Random;

public class TetrisScreen extends Screen {

    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int CELL = 18;

    private final Screen parent;

    private int[][] board = new int[ROWS][COLS];
    private int currentPiece, currentRotation, pieceX, pieceY;
    private int nextPiece;
    private int score, level, lines;
    private boolean gameOver, paused;
    private long lastTick;
    private float dropInterval;
    private final Random random = new Random();

    // I, O, T, S, Z, J, L
    private static final int[][][] SHAPES = {
        {{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}},
        {{1,1},{1,1}},
        {{0,1,0},{1,1,1},{0,0,0}},
        {{0,1,1},{1,1,0},{0,0,0}},
        {{1,1,0},{0,1,1},{0,0,0}},
        {{1,0,0},{1,1,1},{0,0,0}},
        {{0,0,1},{1,1,1},{0,0,0}}
    };

    private static final int[] COLORS = {
        0xFF00FFFF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FF00,
        0xFFFF0000, 0xFF0000FF, 0xFFFF8800
    };

    public TetrisScreen(Screen parent) {
        super(Text.literal("Tetris"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        startGame();
    }

    private void startGame() {
        board = new int[ROWS][COLS];
        score = 0; level = 1; lines = 0;
        gameOver = false; paused = false;
        dropInterval = 1.0f;
        nextPiece = random.nextInt(7);
        spawnPiece();
        lastTick = System.nanoTime();
    }

    private void spawnPiece() {
        currentPiece = nextPiece;
        currentRotation = 0;
        nextPiece = random.nextInt(7);
        int size = SHAPES[currentPiece].length;
        pieceX = COLS / 2 - size / 2;
        pieceY = 0;
        if (!valid(currentPiece, currentRotation, pieceX, pieceY)) gameOver = true;
    }

    private int[][] rotated(int piece, int rot) {
        int[][] s = SHAPES[piece];
        int n = s.length;
        int[][] r = new int[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                switch (rot & 3) {
                    case 0 -> r[i][j] = s[i][j];
                    case 1 -> r[i][j] = s[n-1-j][i];
                    case 2 -> r[i][j] = s[n-1-i][n-1-j];
                    case 3 -> r[i][j] = s[j][n-1-i];
                }
        return r;
    }

    private boolean valid(int piece, int rot, int x, int y) {
        int[][] s = rotated(piece, rot);
        for (int r = 0; r < s.length; r++)
            for (int c = 0; c < s.length; c++)
                if (s[r][c] != 0) {
                    int bx = x + c, by = y + r;
                    if (bx < 0 || bx >= COLS || by >= ROWS) return false;
                    if (by >= 0 && board[by][bx] != 0) return false;
                }
        return true;
    }

    private void lock() {
        int[][] s = rotated(currentPiece, currentRotation);
        for (int r = 0; r < s.length; r++)
            for (int c = 0; c < s.length; c++)
                if (s[r][c] != 0) {
                    int by = pieceY + r, bx = pieceX + c;
                    if (by >= 0 && by < ROWS && bx >= 0 && bx < COLS)
                        board[by][bx] = currentPiece + 1;
                }
        clearLines();
        spawnPiece();
    }

    private void clearLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) if (board[r][c] == 0) { full = false; break; }
            if (full) {
                for (int r2 = r; r2 > 0; r2--) board[r2] = board[r2 - 1].clone();
                board[0] = new int[COLS];
                cleared++; r++;
            }
        }
        if (cleared > 0) {
            int[] pts = {0, 100, 300, 500, 800};
            score += pts[Math.min(cleared, 4)] * level;
            lines += cleared;
            level = lines / 10 + 1;
            dropInterval = Math.max(0.05f, 1.0f - (level - 1) * 0.08f);
        }
    }

    private void gameTick() {
        if (gameOver || paused) return;
        long now = System.nanoTime();
        if ((now - lastTick) / 1_000_000_000f >= dropInterval) {
            if (valid(currentPiece, currentRotation, pieceX, pieceY + 1)) pieceY++;
            else lock();
            lastTick = now;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        gameTick();

        int bw = COLS * CELL, bh = ROWS * CELL;
        int bx = (width - bw) / 2;
        int by = (height - bh) / 2;

        ctx.fill(0, 0, width, height, 0xFF0A0014);
        RenderUtil.Round.draw(ctx, bx - 4, by - 4, bw + 8, bh + 8, 4, 0xFF1A0033);

        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int cx = bx + c * CELL, cy = by + r * CELL;
                if (board[r][c] > 0)
                    drawCell(ctx, cx, cy, COLORS[board[r][c] - 1]);
                else
                    ctx.fill(cx, cy, cx + CELL, cy + CELL, 0xFF1A1A2E);
            }

        if (!gameOver) {
            int gy = pieceY;
            while (valid(currentPiece, currentRotation, pieceX, gy + 1)) gy++;
            int[][] s = rotated(currentPiece, currentRotation);
            for (int r = 0; r < s.length; r++)
                for (int c = 0; c < s.length; c++)
                    if (s[r][c] != 0) {
                        int py = gy + r, px = pieceX + c;
                        if (py >= 0)
                            ctx.fill(bx + px * CELL, by + py * CELL, bx + (px + 1) * CELL, by + (py + 1) * CELL, 0x30FFFFFF);
                    }

            s = rotated(currentPiece, currentRotation);
            for (int r = 0; r < s.length; r++)
                for (int c = 0; c < s.length; c++)
                    if (s[r][c] != 0) {
                        int py = pieceY + r, px = pieceX + c;
                        if (py >= 0) drawCell(ctx, bx + px * CELL, by + py * CELL, COLORS[currentPiece]);
                    }
        }

        int sx = bx + bw + 16;
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Следующая:", sx, by, 6, 0xFFAAAAAA);
        int[][] ns = SHAPES[nextPiece];
        for (int r = 0; r < ns.length; r++)
            for (int c = 0; c < ns.length; c++)
                if (ns[r][c] != 0)
                    drawCell(ctx, sx + c * CELL, by + 16 + r * CELL, COLORS[nextPiece]);

        int scoreY = by + 120;
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Счет: " + score, sx, scoreY, 6, 0xFFAAAAAA);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Линии: " + lines, sx, scoreY + 20, 6, 0xFFAAAAAA);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Уровень: " + level, sx, scoreY + 40, 6, 0xFFAAAAAA);

        int instY = scoreY + 80;
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Управление:", sx, instY, 6, 0xFF888888);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "←→ - движение", sx, instY + 16, 5, 0xFF666666);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "↑ - поворот", sx, instY + 30, 5, 0xFF666666);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "↓ - вниз", sx, instY + 44, 5, 0xFF666666);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "Пробел - сброс", sx, instY + 58, 5, 0xFF666666);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "P - пауза", sx, instY + 72, 5, 0xFF666666);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "ESC - назад", sx, instY + 86, 5, 0xFF666666);

        if (gameOver) {
            ctx.fill(0, 0, width, height, 0x88000000);
            String msg = "ИГРА ОКОНЧЕНА";
            float mw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, msg, 14);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, msg, (width - mw) / 2f, height / 2f - 16, 14, 0xFFFF4444);
            String rs = "Нажмите ENTER для рестарта";
            float rw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, rs, 6);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, rs, (width - rw) / 2f, height / 2f + 14, 6, 0xFFAAAAAA);
        } else if (paused) {
            ctx.fill(0, 0, width, height, 0x88000000);
            String msg = "ПАУЗА";
            float mw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, msg, 14);
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, msg, (width - mw) / 2f, height / 2f, 14, 0xFFAAAAAA);
        }
    }

    private void drawCell(DrawContext ctx, float x, float y, int color) {
        RenderUtil.Round.draw(ctx, x + 1, y + 1, CELL - 2, CELL - 2, 2, color);
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
            case GLFW.GLFW_KEY_LEFT -> { if (valid(currentPiece, currentRotation, pieceX - 1, pieceY)) pieceX--; }
            case GLFW.GLFW_KEY_RIGHT -> { if (valid(currentPiece, currentRotation, pieceX + 1, pieceY)) pieceX++; }
            case GLFW.GLFW_KEY_DOWN -> { if (valid(currentPiece, currentRotation, pieceX, pieceY + 1)) pieceY++; }
            case GLFW.GLFW_KEY_UP -> { int nr = (currentRotation + 1) & 3; if (valid(currentPiece, nr, pieceX, pieceY)) currentRotation = nr; }
            case GLFW.GLFW_KEY_SPACE -> { while (valid(currentPiece, currentRotation, pieceX, pieceY + 1)) { pieceY++; score += 2; } lock(); }
        }
        return true;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent != null ? parent : new TitleScreen());
    }

    @Override public boolean shouldPause() { return false; }
}
