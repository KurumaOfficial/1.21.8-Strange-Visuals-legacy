package ru.strange.client.module.impl.utilities;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
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
import ru.strange.client.utils.other.BindUtil;

@IModule(
        name = "ПвП Хелпер",
        description = "Быстрый свап элитры/нагрудника и переключение на пёрл",
        category = Category.Utilities,
        bind = -1
)
public class PvPHelper extends Module {

    /** Индекс слота нагрудника в {@code playerScreenHandler} (стандарт инвентаря Minecraft). */
    private static final int CHEST_ARMOR_SLOT = 6;

    private final BindSettings pearlBind = new BindSettings("Бинд на пёрл", GLFW.GLFW_KEY_P);
    private final BindSettings elytraBind = new BindSettings("Бинд на элитру", GLFW.GLFW_KEY_Y);

    private boolean pearlLatch;
    private boolean elytraLatch;

    public PvPHelper() {
        addSettings(pearlBind, elytraBind);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        boolean pearlDown = BindUtil.isDown(pearlBind.get());
        if (pearlDown && !pearlLatch) {
            switchToItem(Items.ENDER_PEARL);
            pearlLatch = true;
        } else if (!pearlDown) {
            pearlLatch = false;
        }

        boolean elytraDown = BindUtil.isDown(elytraBind.get());
        if (elytraDown && !elytraLatch) {
            toggleElytraChestSwapHotbarOnly();
            elytraLatch = true;
        } else if (!elytraDown) {
            elytraLatch = false;
        }
    }

    private void switchToItem(Item item) {
        if (mc.player == null) return;

        int slot = findInHotbar(item);
        if (slot != -1) {
            mc.player.getInventory().setSelectedSlot(slot);
        }
    }

    private void toggleElytraChestSwapHotbarOnly() {
        if (mc.player == null || mc.interactionManager == null) return;

        // Открываем инвентарь программно если он не открыт
        boolean openedByModule = false;
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            openedByModule = true;
        }

        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            if (openedByModule) mc.setScreen(null);
            return;
        }

        int chestSlotId = CHEST_ARMOR_SLOT;
        if (chestSlotId < 0 || chestSlotId >= mc.player.playerScreenHandler.slots.size()) {
            if (openedByModule) mc.setScreen(null);
            return;
        }

        ItemStack chestStack = mc.player.playerScreenHandler.getSlot(chestSlotId).getStack();
        boolean wearingElytra = !chestStack.isEmpty() && chestStack.getItem() == Items.ELYTRA;

        if (!wearingElytra) {
            int elytraIndex = findInInventory(Items.ELYTRA);
            if (elytraIndex == -1) {
                if (openedByModule) mc.setScreen(null);
                return;
            }

            int sourceSlotId = elytraIndex < 9 ? 36 + elytraIndex : 9 + (elytraIndex - 9);
            swapSlots(sourceSlotId, chestSlotId);
        } else {
            int chestplateIndex = findBestChestplateIndex();
            if (chestplateIndex == -1) {
                if (openedByModule) mc.setScreen(null);
                return;
            }

            int sourceSlotId = chestplateIndex < 9 ? 36 + chestplateIndex : 9 + (chestplateIndex - 9);
            swapSlots(sourceSlotId, chestSlotId);
        }

        if (openedByModule) mc.setScreen(null);
    }

    private void swapSlots(int slotA, int slotB) {
        if (mc.player == null || mc.interactionManager == null) return;

        int syncId = mc.player.currentScreenHandler.syncId;

        mc.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slotB, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, mc.player);
    }

    private int findInHotbar(Item item) {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Ищет предмет во всём инвентаре игрока (хотбар + основной инвентарь).
     * @return индекс в getInventory().getStack() (0-8 = хотбар, 9-35 = основной)
     */
    private int findInInventory(Item item) {
        if (mc.player == null) return -1;

        // Сначала ищем в хотбаре (приоритет)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }

        // Затем в основном инвентаре
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }

        return -1;
    }

    /**
     * Ищет лучший нагрудник во всём инвентаре.
     * @return индекс в getInventory().getStack()
     */
    private int findBestChestplateIndex() {
        if (mc.player == null) return -1;

        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (item == Items.ELYTRA) continue;
            if (!isChestplate(item)) continue;

            int score = getChestplateScore(item);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private boolean isChestplate(Item item) {
        return item == Items.NETHERITE_CHESTPLATE
                || item == Items.DIAMOND_CHESTPLATE
                || item == Items.IRON_CHESTPLATE
                || item == Items.CHAINMAIL_CHESTPLATE
                || item == Items.GOLDEN_CHESTPLATE
                || item == Items.LEATHER_CHESTPLATE;
    }

    private int getChestplateScore(Item item) {
        if (item == Items.NETHERITE_CHESTPLATE) return 600;
        if (item == Items.DIAMOND_CHESTPLATE) return 500;
        if (item == Items.IRON_CHESTPLATE) return 400;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 300;
        if (item == Items.GOLDEN_CHESTPLATE) return 200;
        if (item == Items.LEATHER_CHESTPLATE) return 100;
        return 0;
    }

}