# 🔍 АУДИТ-ОТЧЁТ: Strange Visuals v1.1.0

**Дата:** 2025  
**Проект:** Strange Visuals — клиентский мод для Minecraft 1.21.8 (Fabric)  
**Репозиторий:** https://github.com/KurumaOfficial/1.21.8-Strange-Visuals-legacy  

---

## 📋 1. ВЕРИФИКАЦИЯ CHANGELOG

### ✅ Подтверждённые изменения (есть в changelog)

| Запись | Статус | Комментарий |
|--------|--------|-------------|
| `[+] AutoRespawn` | ✅ Подтверждён | Новый модуль `AutoRespawn.java` |
| `[+] AutoSwap` | ✅ Подтверждён | Новый модуль `AutoSwap.java` |
| `[+] PvP Helper` | ✅ Подтверждён | Новый модуль `PvPHelper.java` |
| `[+] ShiftTap` | ✅ Подтверждён | Новый модуль `ShiftTap.java` |
| `[+] Hud` | ✅ Подтверждён | `WaterMark.java` переписан с ~130 до ~2091 строки (полная HUD-система) |
| `[+] Optimization` | ✅ Подтверждён | Новый модуль `Optimization.java` |
| `[/] Hat` | ✅ Подтверждён | Добавлены настройки: 2 цвета, альфа, сегменты, контур, вращение |
| `[/] TargetEsp` | ✅ Подтверждён | Добавлены SliderSetting для размеров/количества |
| `[/] FreeLook` | ✅ Подтверждён | Упрощён: убраны Rotation/RotationHandler, добавлен `onDisable()` |
| `[/] [!] NoRender` | ✅ Подтверждён | Добавлены "Убрать тыкву" и "Убрать портал" (тряска default → `true`) |

### ❌ НЕ указано в changelog (ПРОПУЩЕНО!)

| Изменение | Тип | Серьёзность |
|-----------|-----|-------------|
| **FakePlayer.java** — новый модуль фейкового игрока для тестирования | `[+] Новый` | 🔴 КРИТИЧНО |
| **StarterMenu/** — 4 новых файла (AltManagerScreen, NickGenerator, StarterMenuScreen, StrangeVisualsClient) — кастомное главное меню и менеджер аккаунтов | `[+] Новый` | 🔴 КРИТИЧНО |
| **CameraMixin.java** — полная переработка для FreeLook (redirect вместо EventRotation) | `[/] Изменён` | 🟠 ВАЖНО |
| **InGameHudMixin.java** — удалён хук renderer library, добавлены cancel для портала/потионов | `[/] Изменён` | 🟠 ВАЖНО |
| **InGameOverlayRendererMixin.java** — добавлен cancel для тыквы | `[/] Изменён` | 🟡 СРЕДНЕЕ |
| **GpsNavigator** — УДАЛЁН из Manager.java (больше не регистрируется) | `[-] Удалён` | 🟠 ВАЖНО |
| **ClientPlayNetworkHandlerMixin** — УДАЛЁН из mixins JSON | `[-] Удалён` | 🟠 ВАЖНО |

### ⚠️ Неточности changelog

1. Изменение default-значения "Убрать тряску" с `false` → `true` в NoRender не упомянуто как breaking change
2. WaterMark стал HUD-системой с 7+ виджетами — "Hud" в changelog недостаточно описательный

---

## 🐛 2. КРИТИЧЕСКИЕ ПРОБЛЕМЫ КОДА

### ℹ️ CRIT-01: StarterMenu — намеренно не зарегистрирован (dead code by design)
**Файлы:** `StarterMenu/StrangeVisualsClient.java`  
**Описание:** `StrangeVisualsClient` реализует `ClientModInitializer`, но НЕ зарегистрирован в `fabric.mod.json` как entrypoint. Весь пакет `StarterMenu/` (AltManagerScreen, NickGenerator, StarterMenuScreen) — мёртвый код, который никогда не вызывается.  
**Рекомендация:** Либо зарегистрировать в fabric.mod.json, либо удалить. Регистрация двух `ClientModInitializer` требует явного указания обоих.

### ✅ ~~CRIT-02: Массовое создание Color объектов в render()~~ — ИСПРАВЛЕНО
**Файлы:** `AltManagerScreen.java`, `StarterMenuScreen.java`  
**Описание:** В методе `render()` создаётся 50+ объектов `new Color(...)` КАЖДЫЙ КАДР (60+ FPS = 3000+ аллокаций/сек). Это создаёт огромное давление на GC.  
**Пример:** `new Color(10, 10, 12, (int)(12 * fade * a)).getRGB()` в циклах рендеринга.  
**Рекомендация:** Использовать предрассчитанные int ARGB-значения через битовые операции: `(alpha << 24) | (r << 16) | (g << 8) | b`

### ✅ ~~CRIT-03: Рефлексия каждый тик в WaterMark~~ — ИСПРАВЛЕНО
**Файлы:** `WaterMark.java` (NEW)  
**Описание:** Метод `readCooldownStates()` вызывает `getDeclaredFields()`, `getClass().getMethods()`, `Method.invoke()`, `Field.get()` КАЖДЫЙ ТИКАТ (20 раз/сек). Reflection крайне медленная, создаёт массу мусора для GC.  
**Рекомендация:** Кешировать Field/Method объекты в статических полях, инициализировать один раз.

### ✅ ~~CRIT-04: ThreadLocalRandom сохранён в поле~~ — ИСПРАВЛЕНО
**Файл:** `NickGenerator.java`  
**Описание:** `this.rng = b.seed != null ? new Random(b.seed) : ThreadLocalRandom.current()` — результат `ThreadLocalRandom.current()` сохраняется в поле `Random rng`. `ThreadLocalRandom` привязан к потоку и НЕ должен сохраняться в полях — при использовании из другого потока будет race condition.  
**Рекомендация:** Всегда вызывать `ThreadLocalRandom.current()` непосредственно при использовании.

### ✅ ~~CRIT-05: InGameHudMixin — удалён renderer library hook~~ — ИСПРАВЛЕНО
**Файл:** `InGameHudMixin.java`  
**Описание:** В OLD версии вызывался `RenderEvents.HUD.invoker().rendered(context)` из renderer library. В NEW это удалено. Если какой-либо другой код в проекте зависит от этого события — он сломается.  
**Рекомендация:** Проверить все зависимости от `RenderEvents.HUD`.

---

## 🟠 3. ЗНАЧИТЕЛЬНЫЕ ПРОБЛЕМЫ

### ✅ ~~WARN-01: WaterMark — God Class~~ — ИСПРАВЛЕНО (2109 → 211 строк + 8 HUD-элементов)
**Описание:** Декомпозирован в `hud/` пакет: HudElement, WatermarkBar, ModuleListHud, TargetHud, InventoryHud, PotionHud, CooldownHud, CoordsHud.

### ✅ ~~WARN-02: FakePlayer — playSound с null~~ — ИСПРАВЛЕНО
**Файл:** `FakePlayer.java`  
**Описание:** `mc.world.playSound(null, ...)` — null как source entity может не воспроизвести звук корректно на клиенте.  
**Рекомендация:** Использовать `mc.world.playSound(mc.player, ...)`.

### WARN-03: Удалён GpsNavigator без следа
**Описание:** В OLD Manager.java зарегистрирован `new GpsNavigator()`, в NEW — удалён. Файл класса предположительно остался. Это нигде не задокументировано.

### WARN-04: Удалён ClientPlayNetworkHandlerMixin
**Описание:** Миксин удалён из JSON, но причина не указана. Если класс остался в коде — это мёртвый код.

### WARN-05: NickGenerator — overengineered (1073 строки)
**Описание:** Генератор ников имеет 30+ стратегий, phonetic engine, Markov-chain, blender, leet-transform, 10 тем — для функции "случайный ник" в Alt Manager экране, который сам по себе мёртвый код (CRIT-01).

### WARN-06: Hat — unused import RotationAxis
**Файл:** `Hat.java` (OLD)  
**Описание:** Import `RotationAxis` в OLD версии не используется. В NEW — используется для rotation feature. Не критично.

---

## 🟡 4. СТИЛИСТИЧЕСКИЕ ПРОБЛЕМЫ

| # | Проблема | Файлы |
|---|----------|-------|
| STYLE-01 | Пакет `StarterMenu` нарушает Java конвенцию (должен быть `startermenu` — lowercase) | StarterMenu/*.java |
| STYLE-02 | Пустые description в `@IModule` аннотациях | AutoRespawn, AutoSwap, PvPHelper, ShiftTap, FakePlayer, Optimization |
| STYLE-03 | Русский язык в настройках смешан с латинскими именами классов | Все модули |
| STYLE-04 | `hardcoded` путь `"C:\\"` в `Strange.java` (preRoot) | Strange.java |
| STYLE-05 | `StarterMenuScreen.java` показывает `v1.0 · 1.21.8` вместо v1.1.0 | StarterMenuScreen.java |

---

## 📊 5. СВОДКА ИЗМЕНЕНИЙ NEW vs OLD

### Новые файлы (10):
```
ru/strange/client/module/impl/utilities/AutoRespawn.java
ru/strange/client/module/impl/utilities/AutoSwap.java
ru/strange/client/module/impl/utilities/PvPHelper.java
ru/strange/client/module/impl/utilities/ShiftTap.java
ru/strange/client/module/impl/utilities/FakePlayer.java
ru/strange/client/module/impl/other/Optimization.java
ru/strange/client/StarterMenu/AltManagerScreen.java
ru/strange/client/StarterMenu/NickGenerator.java
ru/strange/client/StarterMenu/StarterMenuScreen.java
ru/strange/client/StarterMenu/StrangeVisualsClient.java
```

### Изменённые файлы (8):
```
ru/strange/client/module/impl/player/Hat.java          — расширен настройками
ru/strange/client/module/impl/player/TargetESP.java    — добавлены слайдеры
ru/strange/client/module/impl/utilities/FreeLook.java  — упрощён
ru/strange/client/module/impl/other/NoRender.java      — добавлены опции
ru/strange/client/module/impl/interfaces/WaterMark.java — полная переработка в HUD
ru/strange/client/module/api/Manager.java              — новые модули, удалён GpsNavigator
ru/strange/client/mixin/InGameHudMixin.java            — переработан
ru/strange/client/mixin/CameraMixin.java               — переработан для FreeLook
ru/strange/client/mixin/InGameOverlayRendererMixin.java — добавлен cancel тыквы
```

### Неизменённые файлы:
```
ru/strange/client/Strange.java                   — идентичен
ru/strange/client/module/api/Category.java       — идентичен
ru/strange/client/mixin/GameRendererMixin.java   — идентичен
ru/strange/client/mixin/MinecraftClientMixin.java — идентичен
fabric.mod.json                                  — идентичен
renderer.mixins.json                             — идентичен
```

### Удалённое:
```
strange-visuals.mixins.json: ClientPlayNetworkHandlerMixin — удалён из списка
Manager.java: GpsNavigator — удалён из регистрации
```

---

## ✅ 6. ПОЛОЖИТЕЛЬНЫЕ ИЗМЕНЕНИЯ

1. **Hat.java** — отличная работа по расширению настроек, gradient-рендеринг с двумя цветами
2. **FreeLook.java** — правильно добавлен `onDisable()` для очистки состояния
3. **NoRender.java** — полезные новые опции отключения оверлеев
4. **Optimization.java** — хорошая архитектура save/restore настроек
5. **ShiftTap.java** — чистый, компактный код с правильной crit-детекцией
6. **AutoRespawn.java** — простой и эффективный
7. **FakePlayer.java** — продуманная архитектура с totem-pop симуляцией
8. **CameraMixin.java** (NEW) — правильный подход с @Redirect для FreeLook

---

## 🔧 7. ПЛАН ИСПРАВЛЕНИЙ

### Приоритет 1 (Критические):
- [x] **CRIT-02** ✅ ИСПРАВЛЕНО: Добавлен `argb()` хелпер, 35 `.getRGB()` аллокаций в AltManagerScreen и 14 в StarterMenuScreen заменены на побитовые операции
- [x] **CRIT-03** ✅ ИСПРАВЛЕНО: 7 методов рефлексии закешированы (`ConcurrentHashMap` для иконок/текстур, `Method`/`Field` кеши для cooldown-системы)
- [x] **CRIT-04** ✅ ИСПРАВЛЕНО: `ThreadLocalRandom.current()` заменён на `new Random()`, удалён неиспользуемый импорт

### Приоритет 2 (Важные):
- [x] CRIT-01: ~~Решить судьбу StarterMenu~~ — **намеренно, не трогаем**
- [x] **CRIT-05** ✅ ИСПРАВЛЕНО: `RenderEvents.HUD.invoker().rendered(context)` возвращён в InGameHudMixin с профайлером
- [x] **WARN-02** ✅ ИСПРАВЛЕНО: `null` → `mc.player` во всех 3 вызовах `playSound()` в FakePlayer
- [x] **STYLE-05** ✅ ИСПРАВЛЕНО: Версия `v1.0` → `v1.1.0` в StarterMenuScreen

### Приоритет 3 (Стиль):
- [ ] STYLE-02: Заполнить пустые description
- [ ] Дополнить changelog пропущенными изменениями
