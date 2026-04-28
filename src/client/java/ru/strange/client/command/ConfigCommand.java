package ru.strange.client.command;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.manager.cfg.Config;
import ru.strange.client.module.impl.interfaces.ClickGui;
import ru.strange.client.ui.clickgui.GuiClient;
import ru.strange.client.ui.clickgui.newstyle.NewGuiClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ConfigCommand extends Command {
    private static final List<ActionSpec> ACTIONS = List.of(
            new ActionSpec("save", true, List.of("s")),
            new ActionSpec("load", true, List.of("l")),
            new ActionSpec("reset", false, List.of("r")),
            new ActionSpec("delete", true, List.of("del", "d")),
            new ActionSpec("list", false, List.of("ls"))
    );

    public ConfigCommand() {
        super("cfg", "Config management", "config", "c");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            openConfigScreen();
            return;
        }

        String action = normalizeAction(args[0]);
        switch (action == null ? "" : action) {
            case "save" -> handleSave(args);
            case "load" -> handleLoad(args);
            case "reset" -> handleReset(args);
            case "delete" -> handleDelete(args);
            case "list" -> handleList();
            default -> {
                CommandManager.sendMessage(ModLocalization.tr("command.cfg.unknown_action", args[0]));
                sendUsage();
            }
        }
    }

    static String normalizeAction(String actionToken) {
        if (actionToken == null) {
            return null;
        }

        String normalized = actionToken.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        for (ActionSpec action : ACTIONS) {
            if (action.matches(normalized)) {
                return action.name();
            }
        }

        return null;
    }

    static boolean isRecognizedActionToken(String actionToken) {
        return normalizeAction(actionToken) != null;
    }

    static boolean actionExpectsName(String actionToken) {
        String normalizedAction = normalizeAction(actionToken);
        if (normalizedAction == null) {
            return false;
        }

        for (ActionSpec action : ACTIONS) {
            if (action.name().equals(normalizedAction)) {
                return action.requiresName();
            }
        }

        return false;
    }

    static String suggestActionToken(String actionToken) {
        String normalized = actionToken == null ? "" : actionToken.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return ACTIONS.get(0).name();
        }

        if (isRecognizedActionToken(normalized)) {
            return normalized;
        }

        return ACTIONS.stream()
                .flatMap(action -> action.allTokens().stream())
                .filter(token -> token.startsWith(normalized))
                .sorted(Comparator
                        .comparingInt((String token) -> token.length())
                        .thenComparing(String::compareToIgnoreCase))
                .findFirst()
                .orElse(null);
    }

    private void handleSave(String[] args) {
        String configName = requireValidConfigName(args, "save");
        if (configName == null) {
            return;
        }

        if (Strange.get.configManager.saveSnapshot(configName)) {
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

        if (Strange.get.configManager.loadSnapshot(configName)) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.loaded", configName));
            return;
        }

        CommandManager.sendMessage(ModLocalization.tr("command.cfg.load_failed", configName));
    }

    private void handleReset(String[] args) {
        if (args.length > 1) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_reset"));
            return;
        }

        if (Strange.get.configManager.resetToDefaults()) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.reset_done"));
            return;
        }

        CommandManager.sendMessage(ModLocalization.tr("command.cfg.reset_failed"));
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
        String activeConfigName = Strange.get.configManager.getActiveConfigName();
        if (configs.isEmpty()) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.none_saved"));
            return;
        }

        CommandManager.sendMessage(ModLocalization.tr("command.cfg.saved_list", configs.size()));
        for (Config config : configs) {
            MutableText line = Text.literal("- ").formatted(Formatting.GRAY)
                    .append(Text.literal(config.getName()).formatted(Formatting.AQUA));
            if (config.getName().equalsIgnoreCase(activeConfigName)) {
                line.append(Text.literal(" [active]").formatted(Formatting.GREEN));
            }
            CommandManager.sendMessage(line);
        }
    }

    private String requireValidConfigName(String[] args, String action) {
        if (args.length < 2) {
            CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_action", action));
            sendNameHint();
            return null;
        }

        String rawName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String normalizedName = Strange.get.configManager.normalizeConfigName(rawName);
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
        CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_reset"));
        CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_delete"));
        CommandManager.sendMessage(ModLocalization.tr("command.cfg.usage_list"));
        sendNameHint();
    }

    private void sendNameHint() {
        CommandManager.sendMessage(Text.literal(ModLocalization.tr("command.cfg.name_hint")).formatted(Formatting.GRAY));
    }

    private void openConfigScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            sendUsage();
            return;
        }

        ClickGui clickGuiModule = ClickGui.getInstance();
        if (clickGuiModule != null && clickGuiModule.isNewStyle()) {
            client.setScreen(new NewGuiClient());
        } else {
            client.setScreen(new GuiClient());
        }

        if (client.mouse != null) {
            client.mouse.unlockCursor();
        }
        CommandManager.sendMessage(Text.literal(ModLocalization.raw("Откройте модуль Клик гуи для управления конфигами")));
    }

    private record ActionSpec(String name, boolean requiresName, List<String> aliases) {
        private boolean matches(String token) {
            if (name.equals(token)) {
                return true;
            }

            for (String alias : aliases) {
                if (alias.equals(token)) {
                    return true;
                }
            }

            return false;
        }

        private List<String> allTokens() {
            ArrayList<String> tokens = new ArrayList<>(1 + aliases.size());
            tokens.add(name);
            tokens.addAll(aliases);
            return tokens;
        }
    }
}
