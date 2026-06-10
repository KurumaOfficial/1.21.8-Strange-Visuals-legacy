package ru.strange.client.localization;

import ru.strange.client.Strange;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalizationFormatUtil {
    private static final Set<String> LOGGED_FORMAT_FAILURES = ConcurrentHashMap.newKeySet();

    private LocalizationFormatUtil() {
    }

    public static String format(String source, String key, String template, Object... args) {
        if (template == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return template;
        }

        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException | NullPointerException exception) {
            String logKey = (source == null ? "unknown" : source) + ":" + (key == null ? template : key);
            if (LOGGED_FORMAT_FAILURES.add(logKey)) {
                Strange.LOGGER.warn("Invalid localization format {} in {}: {}", key, source, template, exception);
            }
            return template;
        }
    }
}
