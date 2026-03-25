package ru.strange.client.utils.other;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;

public final class MinecraftLogFilter extends AbstractFilter {
    private static final String[] NOISY_WARNINGS = {
            "Ignoring player info update for unknown player",
            "Received passengers for unknown entity"
    };
    private static final String[] NOISY_INFO = {
            "[System] [CHAT]",
            "[CHAT]"
    };

    private static boolean installed;

    public MinecraftLogFilter() {
        super(Result.NEUTRAL, Result.DENY);
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }

        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();
            MinecraftLogFilter filter = new MinecraftLogFilter();
            configuration.addFilter(filter);

            LoggerConfig root = configuration.getRootLogger();
            root.addFilter(filter);
            for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
                loggerConfig.addFilter(filter);
            }

            context.updateLoggers();
            installed = true;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null) {
            return Result.NEUTRAL;
        }

        String message = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
        if (message == null || message.isBlank()) {
            return Result.NEUTRAL;
        }

        if (event.getLevel().intLevel() >= Level.WARN.intLevel()) {
            for (String warning : NOISY_WARNINGS) {
                if (message.contains(warning)) {
                    return Result.DENY;
                }
            }
        }

        if (event.getLevel().intLevel() <= Level.INFO.intLevel()) {
            for (String info : NOISY_INFO) {
                if (message.contains(info)) {
                    return Result.DENY;
                }
            }
        }

        return Result.NEUTRAL;
    }
}
