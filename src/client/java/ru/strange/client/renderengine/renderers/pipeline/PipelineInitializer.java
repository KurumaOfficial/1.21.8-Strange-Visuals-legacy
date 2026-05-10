package ru.strange.client.renderengine.renderers.pipeline;


public final class PipelineInitializer {

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        PipelineRegistry.init();
    }

    private PipelineInitializer() {
    }

    public static boolean isInitialized() {
        return initialized;
    }
}