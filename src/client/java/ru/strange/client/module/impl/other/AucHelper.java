package ru.strange.client.module.impl.other;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventKeyInput;
import ru.strange.client.event.impl.EventMouseInput;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.utils.other.BindUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@IModule(
        name = "АукХелпер",
        description = "Команда /ah search с предметом в руке",
        category = Category.Other,
        bind = -1
)
public class AucHelper extends Module {

    private final BindSettings bindSetting = new BindSettings("Бинд", -1);

    private static Map<String, String> ruTranslations;

    public AucHelper() {
        addSettings(bindSetting);
    }

    @EventInit
    public void onKey(EventKeyInput event) {
        if (!enable) return;
        if (mc.currentScreen instanceof ChatScreen) return;
        if (event.action() != GLFW.GLFW_PRESS) return;

        if (BindUtil.matchesKeyboard(bindSetting.get(), event.key())) {
            sendSearchCommand();
        }
    }

    @EventInit
    public void onMouse(EventMouseInput event) {
        if (!enable) return;
        if (mc.currentScreen instanceof ChatScreen) return;
        if (event.action() != GLFW.GLFW_PRESS) return;

        if (BindUtil.matchesMouse(bindSetting.get(), event.button())) {
            sendSearchCommand();
        }
    }

    private void sendSearchCommand() {
        if (mc.player == null) return;

        ItemStack stack = mc.player.getMainHandStack();
        String itemName = getRussianName(stack);
        if (itemName.isBlank()) {
            return;
        }

        String message = "/ah search " + itemName;
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatMessage(message);
        }
    }

    private static String getRussianName(ItemStack stack) {
        if (stack.isEmpty()) return "";
        String key = stack.getItem().getTranslationKey();
        Map<String, String> map = getRuTranslations();
        if (map != null && map.containsKey(key)) {
            return map.get(key);
        }
        return stack.getName().getString();
    }

    private static Map<String, String> getRuTranslations() {
        if (ruTranslations != null) return ruTranslations;
        ruTranslations = new HashMap<>();
        try {
            var resource = net.minecraft.client.MinecraftClient.getInstance()
                    .getResourceManager()
                    .getResource(Identifier.of("minecraft", "lang/ru_ru.json"));
            if (resource.isEmpty()) {
                return ruTranslations;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (var entry : json.entrySet()) {
                    ruTranslations.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (Exception ignored) {
            return ruTranslations;
        }
        return ruTranslations;
    }
}