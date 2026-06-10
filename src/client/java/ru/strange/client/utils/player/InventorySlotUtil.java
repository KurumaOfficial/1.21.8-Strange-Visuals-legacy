package ru.strange.client.utils.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public final class InventorySlotUtil {
    private InventorySlotUtil() {
    }

    public static int findInventoryIndex(PlayerEntity player, Item item) {
        if (player == null || item == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    public static int toPlayerScreenSlotId(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= 36) {
            return -1;
        }

        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    public static int firstEmptyHotbarSlot(PlayerEntity player) {
        if (player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public static boolean swapSlots(MinecraftClient mc, int slotA, int slotB) {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return false;
        }

        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slotB, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, mc.player);
        return mc.player.currentScreenHandler.getCursorStack().isEmpty();
    }
}
