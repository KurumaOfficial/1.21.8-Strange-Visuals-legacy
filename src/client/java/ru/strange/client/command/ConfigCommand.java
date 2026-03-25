package ru.strange.client.command;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.manager.cfg.Config;

import java.util.ArrayList;
import java.util.Locale;

public class ConfigCommand extends Command {
    public ConfigCommand() {
        super("cfg", "Config management", "config", "c");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "save", "s" -> handleSave(args);
            case "load", "l" -> handleLoad(args);
            case "delete", "del", "d" -> handleDelete(args);
            case "list", "ls" -> handleList();
            default -> {
                CommandManager.sendMessage(ModLocalization.tr("command.cfg.unknown_action", action));
                sendUsage();
            }
        }
    }

    private void handleSave(String[] args) {
        String configName = requireValidConfigName(args, "save");
        if (configName == null) {
            return;
        }

        if (Strange.get.configManager.saveConfig(configName)) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.saved", configName));
            return;
        }

        CommandManager.sendMessage(ModLocalization.tr("command.cfg.save_failed", configName));
    }

    private void handleLoad(String[] args) {
        String configName = requireValidConfigName(args, "load");
        if (configName == null) {
            return;
        }

        if (Strange.get.configManager.loadConfig(configName)) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.loaded", configName));
            return;
        }

        CommandManager.sendMessage(ModLocalization.tr("command.cfg.load_failed", configName));
    }

    private void handleDelete(String[] args) {
        String configName = requireValidConfigName(args, "delete");
        if (configName == null) {
            return;
        }

        if (Strange.get.configManager.deleteConfig(configName)) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.deleted", configName));
            return;
        }

        CommandManager.sendMessage(ModLocalization.tr("command.cfg.delete_failed", configName));
    }

    private void handleList() {
        ArrayList<Config> configs = Strange.get.configManager.getLoadedConfigs();
        if (configs.isEmpty()) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.none_saved"));
            return;
        }

        CommandManager.sendMessage(ModLocalization.tr("command.cfg.saved_list", configs.size()));
        for (Config config : configs) {
            MutableText line = Text.literal("- ").formatted(Formatting.GRAY)
                    .append(Text.literal(config.getName()).formatted(Formatting.AQUA));
            CommandManager.sendMessage(line);
        }
    }

    private String requireValidConfigName(String[] args, String action) {
        if (args.length < 2) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_action", action));
            sendNameHint();
            return null;
        }

        String normalizedName = Strange.get.configManager.normalizeConfigName(args[1]);
        if (normalizedName == null) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.invalid_name"));
            sendNameHint();
            return null;
        }

        return normalizedName;
    }

    private void sendUsage() {
        CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_title"));
        CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_save"));
        CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_load"));
        CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_delete"));
        CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_list"));
        sendNameHint();
    }

    private void sendNameHint() {
        CommandManager.sendMessage(Text.literal(ModLocalization.tr("command.cfg.name_hint")).formatted(Formatting.GRAY));
    }
}
