# Cape System Backup - 2026-05-09

## Файлы в архиве:
1. **CapeUtil.java** - Менеджер кастомных плащей
2. **CapeCommand.java** - Команда для управления плащами

## Интеграция:

### PlayerListEntryMixin.java
```java
import ru.strange.client.utils.other.CapeUtil;

// В методе strange$injectSkinUtil добавить:
// Применяем кастомные плащи для всех игроков (Привет Горелкинг)
var playerEntity = client.world.getPlayerByUuid(this.profile.getId());
if (playerEntity != null) {
    SkinTextures capeUpdated = CapeUtil.updatedPlayerSkin(original, playerEntity);
    if (capeUpdated != original) {
        cir.setReturnValue(capeUpdated);
    }
}
```

### CommandManager.java
```java
public CommandManager() {
    commands.add(new ConfigCommand());
    commands.add(new GPSCommand());
    commands.add(new CodeCommand());
    commands.add(new CapeCommand()); // Привет Горелкинг - команда для кастомных плащей
}
```

## Использование:
- `.cape load` - загрузить плащ
- `.cape reset` - удалить плащ
- `.cape info` - информация

## Особенности:
- Формат: PNG 64x32 или 22x17
- Хранение: .minecraft/strange/capes/custom_cape.png
- Видимость: все игроки с модом видят плащи друг друга
- Автозагрузка при старте игры
