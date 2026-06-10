package ru.strange.client.utils.render;

import net.minecraft.client.gui.screen.Screen;

public final class PauseMenuPreviewLayout {
    private PauseMenuPreviewLayout() {
    }

    public record Bounds(int x1, int y1, int x2, int y2) {
        public int width() {
            return x2 - x1;
        }

        public int height() {
            return y2 - y1;
        }
    }

    public static Bounds getPreviewBounds(Screen screen) {
        int margin = Math.max(16, screen.width / 40);
        int previewWidth = Math.min(450, Math.max(220, screen.width / 3));
        int x2 = screen.width - margin;
        int x1 = Math.max(screen.width / 2 + 12, x2 - previewWidth);

        int y1 = Math.max(32, screen.height / 12);
        int previewHeight = Math.min(320, Math.max(220, screen.height - y1 - 72));
        int y2 = Math.min(screen.height - 20, y1 + previewHeight);

        return new Bounds(x1, y1, x2, y2);
    }
}
