package ru.strange.client.module.impl.utilities;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Авто Свап",
        description = "",
        category = Category.Utilities,
        bind = -1
)
public class AutoSwap extends Module {

    private final ModeSetting from = new ModeSetting("Свап с", "Тотем", "Тотем", "Сфера");
    private final ModeSetting to = new ModeSetting("Свап на", "Тотем", "Тотем", "Сфера");
    private final BindSettings swapKey = new BindSettings("Кнопка свапа", GLFW.GLFW_KEY_G);
    private final SliderSetting delay = new SliderSetting("Задержка", 150, 100, 250, 25, false);

    private boolean swap;
    private boolean opened;
    private boolean lastKeyState;

    private long keyCooldownTime;
    private long swapStartTime;

    private int pendingSlotId = -1;
    private Item pendingItem = Items.AIR;

    public AutoSwap() {
        addSettings(from, to, swapKey, delay);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (swapKey.get() == -1) return;

        boolean pressed = isKeyDown(swapKey.get());

        if (pressed && !lastKeyState) {
            long now = System.currentTimeMillis();
            if (now - keyCooldownTime >= 250L) {
                swap = true;
                opened = false;
                pendingSlotId = -1;
                pendingItem = Items.AIR;
                swapStartTime = now;
                keyCooldownTime = now;
            }
        }

        lastKeyState = pressed;

        if (!swap) return;

        // Если игрок открыл что-то кроме инвентаря — стопаем
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
            resetSwap();
            return;
        }

        ItemStack offhandStack = mc.player.getOffHandStack();
        Item offhandItem = offhandStack.getItem();

        Item fromItem = getItemByType(from.get());
        Item toItem = getItemByType(to.get());

        if (fromItem == Items.AIR || toItem == Items.AIR) {
            resetSwap();
            return;
        }

        if (pendingItem == Items.AIR) {
            pendingItem = resolveTargetItem(offhandItem, fromItem, toItem);

            if (pendingItem == Items.AIR) {
                resetSwap();
                return;
            }

            int slot = getBestSlotForItem(pendingItem);
            if (slot == -1) {
                resetSwap();
                return;
            }

            pendingSlotId = slot;

            // Если предмет в обычном инвентаре — открываем инвентарь как легит
            if (pendingSlotId <= 35 && !(mc.currentScreen instanceof InventoryScreen)) {
                mc.setScreen(new InventoryScreen(mc.player));
                opened = true;
                swapStartTime = System.currentTimeMillis();
                return;
            }

            swapStartTime = System.currentTimeMillis();
        }

        if (System.currentTimeMillis() - swapStartTime < (long) delay.get()) {
            return;
        }

        doSwap();
    }

    private void doSwap() {
        if (mc.player == null || mc.interactionManager == null || pendingSlotId == -1) {
            resetSwap();
            return;
        }

        int syncId = mc.player.currentScreenHandler.syncId;

        // Берём предмет из слота
        mc.interactionManager.clickSlot(syncId, pendingSlotId, 0, SlotActionType.PICKUP, mc.player);

        // Кладём его в оффхенд
        mc.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, mc.player);

        // Если в курсоре остался старый предмет из оффхенда — кладём назад
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(syncId, pendingSlotId, 0, SlotActionType.PICKUP, mc.player);
        }

        if (opened && mc.currentScreen instanceof InventoryScreen) {
            mc.setScreen(null);
        }

        resetSwap();
    }

    private Item resolveTargetItem(Item offhandItem, Item fromItem, Item toItem) {
        if (fromItem == toItem) {
            return fromItem;
        }

        if (offhandItem == fromItem) {
            return toItem;
        }

        if (offhandItem == toItem) {
            return fromItem;
        }

        return fromItem;
    }

    private Item getItemByType(String type) {
        return switch (type) {
            case "Тотем" -> Items.TOTEM_OF_UNDYING;
            case "Сфера" -> Items.PLAYER_HEAD;
            default -> Items.AIR;
        };
    }

    /**
     * Возвращает slotId для clickSlot:
     * 0-8 hotbar -> 36-44
     * 9-35 inventory -> 9-35
     */
    private int getBestSlotForItem(Item item) {
        if (mc.player == null) return -1;

        // 1. хотбар
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i + 36;
            }
        }

        // 2. зачарованный стак
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item && stack.hasGlint()) {
                return i;
            }
        }

        // 3. обычный стак
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    private void resetSwap() {
        swap = false;
        opened = false;
        pendingSlotId = -1;
        pendingItem = Items.AIR;
        swapStartTime = 0L;
    }

    @Override
    public void onDisable() {
        if (opened && mc.currentScreen instanceof InventoryScreen) {
            mc.setScreen(null);
        }

        resetSwap();
        lastKeyState = false;
        super.onDisable();
    }

    public static boolean isKeyDown(int keyCode) {
        return InputUtil.isKeyPressed(
                MinecraftClient.getInstance().getWindow().getHandle(),
                keyCode
        );
    }
}