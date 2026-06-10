package ru.strange.client.module.impl.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import ru.strange.client.event.Event;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventTotemPop;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.utils.Helper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@IModule(
        name = "Totem Pop Counter",
        description = "Считает срабатывания тотемов у противников",
        category = Category.Player,
        bind = -1
)
public class TotemPopCounter extends Module implements Helper {

    private static volatile TotemPopCounter instance;
    private final Map<UUID, Integer> totemPops = new HashMap<>();
    private final Map<UUID, Long> lastPopTime = new HashMap<>();

    private final BooleanSetting showMessage = new BooleanSetting("Показывать сообщение", true);
    private final BooleanSetting showInChat = new BooleanSetting("В чате", true);
    private final BooleanSetting showOnScreen = new BooleanSetting("На экране", true);

    public TotemPopCounter() {
        if (instance != null) {
            throw new IllegalStateException("TotemPopCounter module already initialized");
        }
        instance = this;
        addSettings(showMessage, showInChat, showOnScreen);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        EventManager.register(this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        EventManager.unregister(this);
        totemPops.clear();
        lastPopTime.clear();
    }

    public void onTotemPop(EventTotemPop event) {
        if (!enable) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity)) return;

        UUID uuid = entity.getUuid();
        String name = entity.getName().getString();

        int count = totemPops.getOrDefault(uuid, 0) + 1;
        totemPops.put(uuid, count);
        lastPopTime.put(uuid, System.currentTimeMillis());

        if (showMessage.get()) {
            String message = "§c" + name + " §eпотерял тотем §6(" + count + ")";
            
            if (showInChat.get() && Helper.mc.player != null) {
                Helper.mc.player.sendMessage(Text.literal(message), false);
            }
        }
    }

    public int getTotemPops(UUID uuid) {
        return totemPops.getOrDefault(uuid, 0);
    }

    public int getTotemPops(Entity entity) {
        return entity == null ? 0 : getTotemPops(entity.getUuid());
    }

    public static TotemPopCounter getInstance() {
        return instance;
    }
}
