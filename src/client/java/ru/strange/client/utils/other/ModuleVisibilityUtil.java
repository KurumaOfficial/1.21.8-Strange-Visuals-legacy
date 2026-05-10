package ru.strange.client.utils.other;

import ru.strange.client.module.api.Module;

public final class ModuleVisibilityUtil {

    private ModuleVisibilityUtil() {
    }

    public static boolean shouldShow(Module module) {
        return ServerRestrictionManager.shouldShow(module);
    }
}
