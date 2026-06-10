package ru.strange.client.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CommandManager {
    private static final String PREFIX = ".";
    private static final char SECTION_SIGN = '\u00A7';
    private static final List<String> GPS_ACTIONS = List.of("clear");
    private final List<Command> commands = new ArrayList<>();

    public CommandManager() {
        commands.add(new ConfigCommand());
        commands.add(new GPSCommand());
        commands.add(new CodeCommand()); // Привет Горелкинг - команда с пасхалками
        commands.add(new CapeCommand()); // Привет Горелкинг - команда для кастомных плащей
        commands.add(new PromoCommand()); // Команда для управления промокодами
    }

    public boolean handleCommand(String message) {
        if (message == null || !message.startsWith(PREFIX)) {
            return false;
        }

        String commandText = message.substring(PREFIX.length()).trim();
        if (commandText.isEmpty()) {
            sendCommandOverview();
            return true;
        }

        String[] parts = commandText.split("\\s+");
        if (parts.length == 0) {
            sendCommandOverview();
            return true;
        }
        String commandName = parts[0].toLowerCase(Locale.ROOT);
        String[] args = new String[Math.max(0, parts.length - 1)];
        System.arraycopy(parts, 1, args, 0, args.length);

        Command command = resolveCommand(commandName);
        if (command == null) {
            sendMessage(ModLocalization.tr("command.error.unknown", commandName));
            sendCommandOverview();
            return true;
        }

        try {
            command.execute(args);
            return true;
        } catch (RuntimeException exception) {
            Strange.LOGGER.warn("Failed to execute command {} with args {}", command.getName(), java.util.Arrays.toString(args), exception);
            String errorMessage = exception.getMessage();
            sendError(ModLocalization.tr("command.error.execution",
                    errorMessage == null || errorMessage.isBlank() ? exception.getClass().getSimpleName() : errorMessage));
            return true;
        }
    }

    private void sendCommandOverview() {
        sendMessage(Text.literal("Available commands:").formatted(Formatting.YELLOW));
        for (Command command : commands) {
            MutableText line = Text.literal("- ").formatted(Formatting.GRAY)
                    .append(Text.literal(PREFIX + command.getName()).formatted(Formatting.AQUA));
            if (!command.getAliases().isEmpty()) {
                String aliases = command.getAliases().stream()
                        .map(alias -> PREFIX + alias)
                        .collect(java.util.stream.Collectors.joining(", "));
                line.append(Text.literal(" (" + aliases + ")").formatted(Formatting.DARK_AQUA));
            }
            if (command.getDescription() != null && !command.getDescription().isBlank()) {
                line.append(Text.literal(" - " + command.getDescription()).formatted(Formatting.WHITE));
            }
            sendMessage(line);
        }
    }

    public static void sendMessage(String message) {
        sendMessage(legacyText(message));
    }

    public static void sendMessage(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(createPrefixedMessage(message), false);
        }
    }

    public static void sendError(String message) {
        sendMessage(Text.literal(message).formatted(Formatting.RED));
    }

    public static MutableText legacyText(String message) {
        return parseLegacyText(message);
    }

    public List<Command> getCommands() {
        return List.copyOf(commands);
    }

    public String getSuggestionSuffix(String message) {
        String suggestion = findBestSuggestion(message);
        if (suggestion == null || suggestion.equals(message)) {
            return null;
        }

        if (!suggestion.regionMatches(true, 0, message, 0, message.length())) {
            return null;
        }

        return suggestion.substring(message.length());
    }

    public String applySuggestion(String message) {
        String suggestion = findBestSuggestion(message);
        return suggestion == null ? message : suggestion;
    }

    private String findBestSuggestion(String message) {
        if (message == null || !message.startsWith(PREFIX)) {
            return null;
        }

        if (commands.isEmpty()) {
            return null;
        }

        if (message.equals(PREFIX)) {
            return PREFIX + commands.get(0).getName() + " ";
        }

        String content = message.substring(PREFIX.length());
        boolean endsWithSpace = message.endsWith(" ");
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return PREFIX + commands.get(0).getName() + " ";
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            return null;
        }

        if (parts.length == 1 && !endsWithSpace) {
            String commandName = findBestMatch(parts[0], collectCommandNames());
            return commandName == null ? null : PREFIX + commandName + " ";
        }

        Command command = resolveCommand(parts[0]);
        if (command == null) {
            return null;
        }

        String commandName = command.getName();
        if ("cfg".equalsIgnoreCase(commandName)) {
            return suggestConfigCommand(content, parts, endsWithSpace, commandName);
        }
        if ("gps".equalsIgnoreCase(commandName)) {
            return suggestGpsCommand(parts, endsWithSpace, commandName);
        }

        return null;
    }

    private String suggestConfigCommand(String content, String[] parts, boolean endsWithSpace, String commandName) {
        if (parts.length == 1 && endsWithSpace) {
            return PREFIX + commandName + " " + ConfigCommand.suggestActionToken("") + " ";
        }

        if (parts.length == 2 && !endsWithSpace) {
            String actionToken = ConfigCommand.suggestActionToken(parts[1]);
            if (actionToken == null) {
                return null;
            }

            return PREFIX + commandName + " " + actionToken + (ConfigCommand.actionExpectsName(actionToken) ? " " : "");
        }

        String actionToken = parts[1].toLowerCase(Locale.ROOT);
        String normalizedAction = ConfigCommand.normalizeAction(actionToken);
        if (normalizedAction == null) {
            String matchedAction = ConfigCommand.suggestActionToken(actionToken);
            if (matchedAction == null) {
                return null;
            }

            return PREFIX + commandName + " " + matchedAction + (ConfigCommand.actionExpectsName(matchedAction) ? " " : "");
        }

        if (!ConfigCommand.actionExpectsName(actionToken)) {
            return PREFIX + commandName + " " + actionToken;
        }

        String partialName = endsWithSpace ? "" : extractRemainderAfterTokens(content, 2);
        List<String> configNames = Strange.get != null && Strange.get.configManager != null
                ? Strange.get.configManager.getLoadedConfigs().stream().map(cfg -> cfg.getName()).toList()
                : List.of();

        if (configNames.isEmpty()) {
            String fallback = Strange.DEFAULT_CONFIG_NAME;
            return partialName.isEmpty() || fallback.toLowerCase(Locale.ROOT).startsWith(partialName.toLowerCase(Locale.ROOT))
                    ? PREFIX + commandName + " " + actionToken + " " + fallback
                    : null;
        }

        String matchedName = findBestMatch(partialName, configNames);
        return matchedName == null ? null : PREFIX + commandName + " " + actionToken + " " + matchedName;
    }

    private String suggestGpsCommand(String[] parts, boolean endsWithSpace, String commandName) {
        if (parts.length == 1 && endsWithSpace) {
            return PREFIX + commandName + " " + GPS_ACTIONS.get(0);
        }

        if (parts.length == 2 && !endsWithSpace) {
            String action = findBestMatch(parts[1], GPS_ACTIONS);
            return action == null ? null : PREFIX + commandName + " " + action;
        }

        return null;
    }

    private List<String> collectCommandNames() {
        List<String> names = new ArrayList<>();
        for (Command command : commands) {
            names.add(command.getName());
            names.addAll(command.getAliases());
        }
        return names;
    }

    private Command resolveCommand(String name) {
        for (Command command : commands) {
            if (command.getName().equalsIgnoreCase(name) || command.getAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(name))) {
                return command;
            }
        }
        return null;
    }

    private String findBestMatch(String input, List<String> options) {
        String normalizedInput = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> normalizedInput.isEmpty() || option.toLowerCase(Locale.ROOT).startsWith(normalizedInput))
                .sorted(Comparator
                        .comparingInt((String option) -> option.length())
                        .thenComparing(String::compareToIgnoreCase))
                .findFirst()
                .orElse(null);
    }

    private String extractRemainderAfterTokens(String text, int tokensToSkip) {
        if (text == null || text.isBlank()) {
            return "";
        }

        int index = 0;
        int skipped = 0;
        while (index < text.length() && skipped < tokensToSkip) {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            while (index < text.length() && !Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            skipped++;
        }

        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }

        return index >= text.length() ? "" : text.substring(index);
    }

    private static Text createPrefixedMessage(Text message) {
        MutableText prefixed = Text.literal("");
        prefixed.append(Text.literal("[").formatted(Formatting.GRAY));
        prefixed.append(Text.literal("Strange").formatted(Formatting.AQUA));
        prefixed.append(Text.literal("] ").formatted(Formatting.GRAY));
        prefixed.append(message);
        return prefixed;
    }

    private static MutableText parseLegacyText(String message) {
        MutableText result = Text.literal("");
        if (message == null || message.isEmpty()) {
            return result;
        }

        Style currentStyle = Style.EMPTY;
        StringBuilder buffer = new StringBuilder();
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            if (character == SECTION_SIGN && index + 1 < message.length()) {
                appendSegment(result, buffer, currentStyle);

                Formatting formatting = Formatting.byCode(Character.toLowerCase(message.charAt(++index)));
                if (formatting == null) {
                    buffer.append(SECTION_SIGN).append(message.charAt(index));
                    continue;
                }

                currentStyle = formatting == Formatting.RESET
                        ? Style.EMPTY
                        : currentStyle.withFormatting(formatting);
                continue;
            }

            buffer.append(character);
        }

        appendSegment(result, buffer, currentStyle);
        return result;
    }

    private static void appendSegment(MutableText result, StringBuilder buffer, Style style) {
        if (buffer.length() == 0) {
            return;
        }

        result.append(Text.literal(buffer.toString()).setStyle(style));
        buffer.setLength(0);
    }
}
