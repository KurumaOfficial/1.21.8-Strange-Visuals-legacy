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
            case "use":
            case "select":
                if (args.length < 2) {
                    CapeUtil.uiPickAndApplyCape();
                    CommandManager.sendMessage(Text.literal("§aОткрывается диалог выбора файла плаща...").formatted(Formatting.GREEN));
                    CommandManager.sendMessage(Text.literal("§7Выберите PNG или GIF файл с текстурой плаща").formatted(Formatting.GRAY));
                } else {
                    CapeUtil.useCapeFromFolder(args[1]);
                }
                break;
                
            case "reset":
            case "remove":
                CapeUtil.uiResetCape();
                break;
                
            case "info":
                showInfo();
                break;

            case "dir":
            case "folder":
            case "open":
                CapeUtil.openCapeDirectory();
                break;

            case "list":
                showCapeList();
                break;
                
            default:
                CommandManager.sendMessage(Text.literal("§cНеизвестная команда: " + action).formatted(Formatting.RED));
                sendUsage();
                break;
        }
    }

    private void sendUsage() {
        CommandManager.sendMessage(Text.literal("§6=== Cape Command Usage ===").formatted(Formatting.GOLD));
        CommandManager.sendMessage(Text.literal("§e.cape load §7- Загрузить плащ через диалог").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.cape load <имя> §7- Применить плащ из папки").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.cape dir §7- Открыть папку с плащами").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.cape list §7- Список плащей в папке").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.cape reset §7- Удалить кастомный плащ").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§e.cape info §7- Информация о плащах").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§7Поддерживаются PNG и GIF (анимированные плащи)").formatted(Formatting.GRAY));
        CommandManager.sendMessage(Text.literal("§7Все игроки с этим модом увидят твой плащ!").formatted(Formatting.GRAY));
    }

    private void showCapeList() {
        var capes = CapeUtil.listCapeFiles();
        if (capes.isEmpty()) {
            CommandManager.sendMessage(Text.literal("§7Папка плащей пуста. Используй §e.cape dir §7и добавь PNG/GIF.").formatted(Formatting.GRAY));
            return;
        }

        CommandManager.sendMessage(Text.literal("§6=== Плащи в папке ===").formatted(Formatting.GOLD));
        for (String cape : capes) {
            CommandManager.sendMessage(Text.literal("§7- §f" + cape).formatted(Formatting.WHITE));
        }
        CommandManager.sendMessage(Text.literal("§7Применить: §e.cape load <имя файла>").formatted(Formatting.GRAY));
    }

    private void showInfo() {
        CommandManager.sendMessage(Text.literal("§6=== Custom Capes Info ===").formatted(Formatting.GOLD));
        CommandManager.sendMessage(Text.literal("§eРазмер текстуры: §764x32 пикселей (стандарт) или 22x17").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§eФормат: §7PNG или GIF (анимированные плащи)").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§eВидимость: §aВсе игроки с модом видят твой плащ").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§eХранение: §7.minecraft/strange/capes/").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§eПапка: §7.cape dir §7- открыть и добавить PNG/GIF").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§eПрименение: §7.cape load <имя файла>").formatted(Formatting.WHITE));
        CommandManager.sendMessage(Text.literal("§7Плащ автоматически загружается при запуске игры").formatted(Formatting.GRAY));
    }
}
