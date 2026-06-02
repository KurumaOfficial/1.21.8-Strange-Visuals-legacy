package ru.strange.client.ui.clickgui;

/**
 * Tracks the visual scale applied while rendering the classic click GUI so input can be unscaled.
 */
public final class GuiRenderScale {

    private static float scale = 1f;
    private static float anchorX;
    private static float anchorY;

    private GuiRenderScale() {
    }

    public static void set(float renderScale, float centerX, float centerY) {
        scale = renderScale;
        anchorX = centerX;
        anchorY = centerY;
    }

    public static float scale() {
        return scale;
    }

    public static float anchorX() {
        return anchorX;
    }

    public static float anchorY() {
        return anchorY;
    }

    public static double[] toLayoutMouse(double mouseX, double mouseY) {
        return GuiMouseUtil.unscaleFromAnchor(mouseX, mouseY, anchorX, anchorY, scale);
    }
}
