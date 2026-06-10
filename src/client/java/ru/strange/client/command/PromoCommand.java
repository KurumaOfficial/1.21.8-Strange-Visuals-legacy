package ru.strange.client.command;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.strange.client.manager.promo.PromoCodeManager;

import java.security.SecureRandom;
import java.util.Locale;

public class PromoCommand extends Command {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;

    public PromoCommand() {
        super("promo", "Управление промокодами");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "activate":
                handleActivate(args);
                break;
            case "generate":
                handleGenerate(args);
                break;
            case "list":
                handleList();
                break;
            case "remove":
                handleRemove(args);
                break;
            default:
                sendUsage();
        }
    }

    private void handleActivate(String[] args) {
        if (args.length < 2) {
            CommandManager.sendMessage(Text.literal("§cИспользование: .promo activate <код>").formatted(Formatting.RED));
            return;
        }

        String code = args[1];
        String clientIp = getClientIp();

        PromoCodeManager.PromoResult result = PromoCodeManager.apply(code, clientIp);

        if (result.accepted) {
            CommandManager.sendMessage(Text.literal("§a✓ " + result.message).formatted(Formatting.GREEN));
            if (result.promoCode != null && !result.promoCode.description.isEmpty()) {
                CommandManager.sendMessage(Text.literal("§e" + result.promoCode.description).formatted(Formatting.YELLOW));
            }
        } else {
            CommandManager.sendMessage(Text.literal("§c✗ " + result.message).formatted(Formatting.RED));
        }
    }

    private void handleGenerate(String[] args) {
        int count = 1;
        if (args.length > 1) {
            try {
                count = Math.min(10, Math.max(1, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                CommandManager.sendMessage(Text.literal("§cНеверное количество!").formatted(Formatting.RED));
                return;
            }
        }

        CommandManager.sendMessage(Text.literal("§6=== Генерация промокодов ===").formatted(Formatting.GOLD));

        for (int i = 0; i < count; i++) {
            String code = generateCode();
            String description = "Промокод #" + (i + 1);
            
            PromoCodeManager.PromoCode promoCode = new PromoCodeManager.PromoCode(
                code,
                PromoCodeManager.TYPE_IP,
                1,
                description
            );
            
            PromoCodeManager.addCode(promoCode);
            
            CommandManager.sendMessage(Text.literal("§e" + code + " §7- " + description).formatted(Formatting.WHITE));
        }

        CommandManager.sendMessage(Text.literal("§a✓ Сгенерировано " + count + " промокодов (1 промокод на 1 IP)").formatted(Formatting.GREEN));
    }

    private void handleList() {
        CommandManager.sendMessage(Text.literal("§6=== Список промокодов ===").formatted(Formatting.GOLD));
        
        var codes = PromoCodeManager.getCodes();
        if (codes.isEmpty()) {
            CommandManager.sendMessage(Text.literal("§7Промокодов нет. Используй .promo generate").formatted(Formatting.GRAY));
            return;
        }

        for (PromoCodeManager.PromoCode code : codes) {
            String typeStr = code.type == PromoCodeManager.TYPE_IP ? "1/IP" : "Один раз";
            CommandManager.sendMessage(Text.literal("§e" + code.code + " §7[" + typeStr + "] §f- " + code.description).formatted(Formatting.WHITE));
        }
    }

    private void handleRemove(String[] args) {
        if (args.length < 2) {
            CommandManager.sendMessage(Text.literal("§cИспользование: .promo remove <код>").formatted(Formatting.RED));
            return;
        }

        String code = args[1];
        PromoCodeManager.removeCode(code);
        CommandManager.sendMessage(Text.literal("§a✓ Промокод удален: " + code).formatted(Formatting.GREEN));
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    private String getClientIp() {
        // В реальном приложении здесь должен быть метод получения IP
        // Для тестирования используем заглушку
        return "127.0.0.1";
    }

    private void sendUsage() {
        CommandManager.sendMessage(Text.literal("§6=== Promo Command Usage ===").formatted(Formatting.GOLD));
        CommandManager.sendMessage(Text.literal("§e.promo activate <код> §7- Активировать промокод").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.promo generate [кол-во] §7- Сгенерировать промокоды").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.promo list §7- Показать список промокодов").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.promo remove <код> §7- Удалить промокод").formatted(Formatting.WHITE));
    }
}
