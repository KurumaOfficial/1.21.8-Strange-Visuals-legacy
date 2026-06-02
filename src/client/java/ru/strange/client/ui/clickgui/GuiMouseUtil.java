package ru.strange.client.ui.clickgui;

/**
 * Maps screen-space mouse coordinates to GUI layout space when render uses scale transforms.
 */
public final class GuiMouseUtil {

    private GuiMouseUtil() {
    }

    public static double[] unscaleFromAnchor(double mouseX, double mouseY, float anchorX, float anchorY, float scale) {
        if (scale < 0.999f || scale > 1.001f) {
            mouseX = anchorX + (mouseX - anchorX) / scale;
            mouseY = anchorY + (mouseY - anchorY) / scale;
        }
        return new double[]{mouseX, mouseY};
    }

    public static double[] panelLocalMouse(double mouseX, double mouseY, float panelX, float panelY,
                                           float panelWidth, float panelHeight, float scale) {
        float scaledW = panelWidth * scale;
        float scaledH = panelHeight * scale;
        float scaleOffX = (panelWidth - scaledW) / 2f;
        float scaleOffY = (panelHeight - scaledH) / 2f;
        float centerX = panelX + scaleOffX + scaledW / 2f;
        float centerY = panelY + scaleOffY + scaledH / 2f;

        float localX = (float) ((mouseX - centerX) / scale + panelWidth / 2f);
        float localY = (float) ((mouseY - centerY) / scale + panelHeight / 2f);
        return new double[]{panelX + localX, panelY + localY};
    }
}
