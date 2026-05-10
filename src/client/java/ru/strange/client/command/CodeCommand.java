package ru.strange.client.command;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.strange.client.Strange;

import java.util.HashMap;
import java.util.Map;

// Привет Горелкинг - система пасхалок с кодами активации
public final class CodeCommand extends Command {
    private static final Map<String, String> EASTER_EGGS = new HashMap<>();
    private static final Map<String, Boolean> CODE_USED = new HashMap<>();

    static {
        // Инициализация пасхалок с индивидуальными кодами (каждый код можно использовать только 1 раз)
        EASTER_EGGS.put("GORELKING2026", "§6✨ Привет Горелкинг! ✨§r\n§eТы нашел секретный код разработчика!");
        EASTER_EGGS.put("STRANGE777", "§d⭐ Strange Visuals Secret ⭐§r\n§5Ты открыл тайну клиента!");
        EASTER_EGGS.put("VISUAL2024", "§b❄ Visual Client Easter Egg ❄§r\n§3Поздравляем с находкой!");
        EASTER_EGGS.put("MATRIX1337", "§2▓ Matrix Mode Unlocked ▓§r\n§aДобро пожаловать в матрицу...");
        EASTER_EGGS.put("DEVELOPER", "§c♦ Developer Access ♦§r\n§4Режим разработчика обнаружен!");
    }

    public CodeCommand() {
        super("code", "Activate secret easter eggs with codes");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        if (args.length == 1 && "list".equalsIgnoreCase(args[0])) {
            showActivatedCodes();
            return;
        }

        String code = String.join("", args).toUpperCase();
        activateCode(code);
    }

    private void activateCode(String code) {
        String message = EASTER_EGGS.get(code);
        
        if (message == null) {
            CommandManager.sendMessage(Text.literal("§cНеверный код! Попробуй другой...").formatted(Formatting.RED));
            return;
        }

        // Проверяем, был ли код уже использован
        if (CODE_USED.getOrDefault(code, false)) {
            CommandManager.sendMessage(Text.literal("§c✗ Этот код уже был использован!").formatted(Formatting.RED));
            CommandManager.sendMessage(Text.literal("§7Каждый код можно активировать только один раз.").formatted(Formatting.GRAY));
            return;
        }

        // Отмечаем код как использованный
        CODE_USED.put(code, true);

        // Показываем пасхалку
        CommandManager.sendMessage(CommandManager.legacyText(message));
        CommandManager.sendMessage(Text.literal("§a✓ Код успешно активирован!").formatted(Formatting.GREEN));

        // Логируем активацию
        Strange.LOGGER.info("Easter egg activated: {}", code);
    }

    private void showActivatedCodes() {
        CommandManager.sendMessage(Text.literal("§6=== Активированные коды ===").formatted(Formatting.GOLD));
        
        if (CODE_USED.isEmpty()) {
            CommandManager.sendMessage(Text.literal("§7Пока не активировано ни одного кода.").formatted(Formatting.GRAY));
            return;
        }

        for (Map.Entry<String, Boolean> entry : CODE_USED.entrySet()) {
            String code = entry.getKey();
            boolean used = entry.getValue();
            
            if (used) {
                CommandManager.sendMessage(Text.literal("§c✗ §e" + code + " §7- использован").formatted(Formatting.WHITE));
            }
        }
    }

    private void sendUsage() {
        CommandManager.sendMessage(Text.literal("§6=== Code Command Usage ===").formatted(Formatting.GOLD));
        CommandManager.sendMessage(Text.literal("§e.code <КОД> §7- Активировать пасхалку").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.code list §7- Показать использованные коды").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§7Подсказка: коды можно найти в комментариях кода!").formatted(Formatting.GRAY));
        CommandManager.sendMessage(Text.literal("§c⚠ Каждый код работает только ОДИН раз!").formatted(Formatting.RED));
    }
}
