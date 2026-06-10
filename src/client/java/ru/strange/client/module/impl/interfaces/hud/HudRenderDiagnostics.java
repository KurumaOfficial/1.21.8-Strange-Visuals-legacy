package ru.strange.client.module.impl.interfaces.hud;

import ru.strange.client.Strange;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

final class HudRenderDiagnostics {
    private static final int MAX_LOGGED_KEYS = 128;
    private static final Object LOG_LOCK = new Object();
    private static final Set<String> LOGGED_KEYS = new HashSet<>();
    private static final Deque<String> LOGGED_KEY_ORDER = new ArrayDeque<>();

    private HudRenderDiagnostics() {
    }

    static void debugOnce(String key, String message, Throwable throwable) {
        if (throwable == null || !rememberKey(key)) {
            return;
        }
        Strange.LOGGER.debug("{} [{}]", message, key, throwable);
    }

    static void warnOnce(String key, String message, Throwable throwable) {
        if (throwable == null || !rememberKey(key)) {
            return;
        }
        Strange.LOGGER.warn("{} [{}]", message, key, throwable);
    }

    private static boolean rememberKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        synchronized (LOG_LOCK) {
            if (!LOGGED_KEYS.add(key)) {
                return false;
            }
            LOGGED_KEY_ORDER.addLast(key);
            if (LOGGED_KEY_ORDER.size() > MAX_LOGGED_KEYS) {
                String oldestKey = LOGGED_KEY_ORDER.removeFirst();
                LOGGED_KEYS.remove(oldestKey);
            }
            return true;
        }
    }
}
