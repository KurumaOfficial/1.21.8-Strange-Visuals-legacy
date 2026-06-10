package ru.strange.client.renderengine.renderers.util;

public class FrameTracker {
    private static int frame;

    public static void onFrameStart() {
        frame++;
    }

    public static int getFrame() {
        return frame;
    }
}

