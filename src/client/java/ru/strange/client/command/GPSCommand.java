package ru.strange.client.command;

import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.impl.utilities.GPS;

public final class GPSCommand extends Command {
    public GPSCommand() {
        super("gps", "GPS route command");
    }

    @Override
    public void execute(String[] args) {
        executeArgs(args);
    }

    public static boolean handleRawMessage(String message) {
        if (message == null) {
            return false;
        }

        String trimmed = message.trim();
        if (!trimmed.regionMatches(true, 0, ".gps", 0, 4)
                || (trimmed.length() > 4 && !Character.isWhitespace(trimmed.charAt(4)))) {
            return false;
        }

        String argsText = trimmed.length() <= 4 ? "" : trimmed.substring(4).trim();
        String[] args = argsText.isEmpty() ? new String[0] : argsText.split("\\s+");
        executeArgs(args);
        return true;
    }

    private static void executeArgs(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        GPS gps = resolveGpsModule();
        if (gps == null || !gps.enable) {
            CommandManager.sendMessage(ModLocalization.tr("command.gps.enable_first"));
            return;
        }

        if (args.length == 1 && "clear".equalsIgnoreCase(args[0])) {
            gps.clearPath();
            return;
        }

        if (args.length != 3) {
            sendUsage();
            return;
        }

        try {
            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            int z = Integer.parseInt(args[2]);
            gps.setTarget(x, y, z);
        } catch (NumberFormatException exception) {
            CommandManager.sendMessage(ModLocalization.tr("command.gps.invalid_coords"));
        }
    }

    private static GPS resolveGpsModule() {
        if (Strange.get == null || Strange.get.manager == null) {
            return null;
        }
        return Strange.get.manager.get(GPS.class);
    }

    private static void sendUsage() {
        CommandManager.sendMessage(ModLocalization.tr("command.gps.usage_title"));
        CommandManager.sendMessage(ModLocalization.tr("command.gps.usage_set"));
        CommandManager.sendMessage(ModLocalization.tr("command.gps.usage_clear"));
    }
}
