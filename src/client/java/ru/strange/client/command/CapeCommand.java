package ru.strange.client.command;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.strange.client.utils.other.CapeUtil;

// Привет Горелкинг - команда для управления кастомными плащами
public final class CapeCommand extends Command {
    
    public CapeCommand() {
        super("cape", "Manage custom capes");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String action = args[0].toLowerCase();
        
        switch (action) {
            case "load":
            case "set":
                CapeUtil.uiPickAndApplyCape();
                CommandManager.sendMessage(Text.literal("§aОткрывается диалог выбора файла плаща...").formatted(Formatting.GREEN));
                CommandManager.sendMessage(Text.literal("§7Выберите PNG файл с текстурой плаща (64x32 или 22x17)").formatted(Formatting.GRAY));
                break;
                
            case "reset":
            case "remove":
                CapeUtil.uiResetCape();
                break;
                
            case "info":
                showInfo();
                break;
                
            default:
                CommandManager.sendMessage(Text.literal("§cНеизвестная команда: " + action).formatted(Formatting.RED));
                sendUsage();
                break;
        }
    }

    private void sendUsage() {
        CommandManager.sendMessage(Text.literal("§6=== Cape Command Usage ===").formatted(Formatting.GOLD));
        CommandManager.sendMessage(Text.literal("§e.cape load §7- Загрузить кастомный плащ").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.cape reset §7- Удалить кастомный плащ").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.cape info §7- Информация о плащах").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§7Все игроки с этим модом увидят твой плащ!").formatted(Formatting.GRAY));
    }

    private void showInfo() {
        CommandManager.sendMessage(Text.literal("§6=== Custom Capes Info ===").formatted(Formatting.GOLD));
        CommandManager.sendMessage(Text.literal("§eРазмер текстуры: §764x32 пикселей (стандарт) или 22x17").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§eФормат: §7PNG с прозрачностью").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§eВидимость: §aВсе игроки с модом видят твой плащ").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§eХранение: §7.minecraft/strange/capes/custom_cape.png").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§7Плащ автоматически загружается при запуске игры").formatted(Formatting.GRAY));
    }
}
