package ru.strange.client.module.impl.utilities;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.utils.other.ServerUtil;
import ru.strange.client.utils.player.InventorySlotUtil;

import java.util.List;
import java.util.Locale;

@IModule(
        name = "Авто подключение (ST)",
        description = "",
        category = Category.Utilities,
        bind = -1
)
public class SpJoiner extends Module {
    private static final long ACTION_DELAY = 300L;
    private static final long CLICK_COOLDOWN = 1_500L;
    private static final long JOINED_CHECK_DELAY = 5_000L;
    private static final double JOIN_DISTANCE_SQ = 24.0D * 24.0D;

    private static final List<String> TARGET_KEYWORDS = List.of("дуэл", "duel", "pvp");
    private static final List<String> ACTIVE_DUEL_KEYWORDS = List.of(
            "соперник", "opponent",
            "карта", "map",
            "набор", "kit",
            "kills", "kill", "килл",
            "wins", "loss", "losses",
            "стрик", "streak",
            "remaining", "осталось",
            "waiting", "ожидание"
    );

    private long lastActionTime;
    private long lastContainerClickTime;
    private long lastTargetClickTime;
    private String lastClickedSignature = "";
    private int clickCount;

    private boolean awaitingJoin;
    private double lastAttemptX;
    private double lastAttemptY;
    private double lastAttemptZ;
    private String lastAttemptDimension = "";

    @Override
    public void onEnable() {
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        String server = ServerUtil.getServerId().toLowerCase(Locale.ROOT);
        if (!server.contains("spookytime") && !server.contains("spooky")) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_DELAY) {
            return;
        }

        lastActionTime = now;

        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            handleContainer(screen, now);
            return;
        }

        if (awaitingJoin) {
            if (hasJoinedDuel()) {
                setState(false);
                return;
            }

            if (lastTargetClickTime > 0 && now - lastTargetClickTime < JOINED_CHECK_DELAY) {
                return;
            }

            awaitingJoin = false;
            lastTargetClickTime = 0L;
        }

        int compassIndex = InventorySlotUtil.findInventoryIndex(mc.player, Items.COMPASS);
        if (compassIndex == -1) {
            return;
        }

        if (compassIndex >= 9) {
            int hotbarTarget = mc.player.getInventory().getSelectedSlot();
            if (mc.player.getInventory().getStack(hotbarTarget).isOf(Items.COMPASS)) {
                compassIndex = hotbarTarget;
            } else {
                int sourceSlotId = InventorySlotUtil.toPlayerScreenSlotId(compassIndex);
                int targetSlotId = InventorySlotUtil.toPlayerScreenSlotId(hotbarTarget);
                if (sourceSlotId == -1 || targetSlotId == -1
                        || !InventorySlotUtil.swapSlots(mc, sourceSlotId, targetSlotId)) {
                    return;
                }
                compassIndex = hotbarTarget;
            }
        }

        ItemStack stack = mc.player.getInventory().getStack(compassIndex);
        if (stack.isEmpty() || !stack.isOf(Items.COMPASS)) {
            return;
        }

        mc.player.getInventory().setSelectedSlot(compassIndex);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void handleContainer(GenericContainerScreen screen, long now) {
        int slot = findTargetSlot(screen);
        if (slot < 0) {
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        ItemStack stack = handler.getSlot(slot).getStack();
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString().toLowerCase(Locale.ROOT);
        String stackName = stack.getName().getString().toLowerCase(Locale.ROOT);
        String signature = title + "#" + slot + "#" + stackName;

        if (signature.equals(lastClickedSignature) && now - lastContainerClickTime < CLICK_COOLDOWN) {
            return;
        }

        rememberJoinAttempt();
        clickContainerSlot(slot);
        lastClickedSignature = signature;
        lastContainerClickTime = now;
        lastTargetClickTime = now;
        awaitingJoin = true;
        clickCount++;
    }

    private int findTargetSlot(GenericContainerScreen screen) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int containerSlots = handler.getRows() * 9;

        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) {
                continue;
            }

            int score = scoreStack(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        if (bestScore > 0) {
            return bestSlot;
        }

        return -1;
    }

    private int scoreStack(ItemStack stack) {
        String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
        int score = 0;

        for (String keyword : TARGET_KEYWORDS) {
            if (displayName.contains(keyword)) {
                score += 100;
            }
        }

        if (stack.isOf(Items.NETHER_STAR) || stack.isOf(Items.DIAMOND_SWORD) || stack.isOf(Items.IRON_SWORD)) {
            score += 25;
        }

        if (stack.isOf(Items.IRON_AXE) || stack.isOf(Items.DIAMOND_AXE)) {
            score += 15;
        }

        return score;
    }

    private void clickContainerSlot(int slot) {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void rememberJoinAttempt() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        lastAttemptX = mc.player.getX();
        lastAttemptY = mc.player.getY();
        lastAttemptZ = mc.player.getZ();
        lastAttemptDimension = mc.world.getRegistryKey().getValue().toString();
    }

    private boolean hasJoinedDuel() {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        if (hasEnderChestInHotbar()) {
            return true;
        }

        if (hasWorldContextChanged()) {
            return true;
        }

        if (hasCombatLoadout()) {
            return true;
        }

        return hasActiveDuelScoreboard();
    }

    private boolean hasEnderChestInHotbar() {
        if (mc.player == null) {
            return false;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(Items.ENDER_CHEST)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasWorldContextChanged() {
        if (mc.player == null || mc.world == null || lastAttemptDimension.isEmpty()) {
            return false;
        }

        String currentDimension = mc.world.getRegistryKey().getValue().toString();
        if (!currentDimension.equals(lastAttemptDimension)) {
            return true;
        }

        double dx = mc.player.getX() - lastAttemptX;
        double dy = mc.player.getY() - lastAttemptY;
        double dz = mc.player.getZ() - lastAttemptZ;
        return dx * dx + dy * dy + dz * dz >= JOIN_DISTANCE_SQ;
    }

    private boolean hasCombatLoadout() {
        if (mc.player == null) {
            return false;
        }

        int combatItems = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (isCombatItem(stack)) {
                combatItems++;
            }
        }

        return combatItems >= 2;
    }

    private boolean isCombatItem(ItemStack stack) {
        return stack.isOf(Items.WOODEN_SWORD)
                || stack.isOf(Items.STONE_SWORD)
                || stack.isOf(Items.IRON_SWORD)
                || stack.isOf(Items.DIAMOND_SWORD)
                || stack.isOf(Items.NETHERITE_SWORD)
                || stack.isOf(Items.WOODEN_AXE)
                || stack.isOf(Items.STONE_AXE)
                || stack.isOf(Items.IRON_AXE)
                || stack.isOf(Items.DIAMOND_AXE)
                || stack.isOf(Items.NETHERITE_AXE)
                || stack.isOf(Items.BOW)
                || stack.isOf(Items.CROSSBOW)
                || stack.isOf(Items.TRIDENT)
                || stack.isOf(Items.FISHING_ROD)
                || stack.isOf(Items.GOLDEN_APPLE)
                || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)
                || stack.isOf(Items.ENDER_PEARL)
                || stack.isOf(Items.SHIELD);
    }

    private boolean hasActiveDuelScoreboard() {
        if (mc.world == null) {
            return false;
        }

        var scoreboard = mc.world.getScoreboard();
        var objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) {
            return false;
        }

        if (containsActiveDuelKeyword(objective.getDisplayName().getString())) {
            return true;
        }

        for (var entry : scoreboard.getScoreboardEntries(objective)) {
            if (entry.hidden()) {
                continue;
            }

            if (containsActiveDuelKeyword(entry.name().getString())) {
                return true;
            }

            var display = entry.display();
            if (display != null && containsActiveDuelKeyword(display.getString())) {
                return true;
            }
        }

        return false;
    }

    private boolean containsActiveDuelKeyword(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String keyword : ACTIVE_DUEL_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void resetState() {
        lastActionTime = 0L;
        lastContainerClickTime = 0L;
        lastTargetClickTime = 0L;
        lastClickedSignature = "";
        clickCount = 0;
        awaitingJoin = false;
        lastAttemptX = 0.0D;
        lastAttemptY = 0.0D;
        lastAttemptZ = 0.0D;
        lastAttemptDimension = "";
    }
}
