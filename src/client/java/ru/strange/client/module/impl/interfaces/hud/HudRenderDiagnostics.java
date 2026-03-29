package ru.strange.client.module.impl.interfaces.hud;

import ru.strange.client.Strange;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class HudRenderDiagnostics {
    private static final Set<String> LOGGED_KEYS = ConcurrentHashMap.newKeySet();

    private HudRenderDiagnostics() {
    }

    static void debugOnce(String key, String message, Throwable throwable) {
        if (throwable == null || !LOGGED_KEYS.add(key)) {
            return;
        }
        Strange.LOGGER.debug("{} [{}]", message, key, throwable);
    }
}
