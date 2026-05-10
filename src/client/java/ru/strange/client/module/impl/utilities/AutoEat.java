package ru.strange.client.module.impl.utilities;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.module.api.setting.impl.StringSetting;
import ru.strange.client.module.api.setting.impl.BooleanSetting;

@IModule(
        name = "Авто еда",
        description = "Автоматически ест еду из хотбара",
        category = Category.Utilities,
        bind = -1
)
public class AutoEat extends Module {
    private static final float EMERGENCY_HEALTH_THRESHOLD = 12.0F;

    private final SliderSetting hungerThreshold = new SliderSetting("Голод", 14, 1, 20, 1, false);
    private final SliderSetting interval = new SliderSetting("Интервал (сек)", 2, 1, 30, 1, false);
    private final StringSetting foodFilter = new StringSetting("Еда", "");
    private final BooleanSetting onlyFiltered = new BooleanSetting("Только указанная", false);

    private long lastEatTime;
    private int savedSlot = -1;
    private int eatingSlot = -1;
    private boolean isEating;
    private int eatTicks;
    private int startingFoodLevel;
    private int startingStackCount;
    private int retryAttempts;
    private boolean forcedUseKey;
    private boolean emergencyEating;
    private float startingCombinedHealth;

    public AutoEat() {
        addSettings(hungerThreshold, interval, foodFilter, onlyFiltered);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) {
            if (isEating) {
                stopEating();
            }
            return;
        }

        if (isEating) {
            tickEating();
            return;
        }

        int foodLevel = mc.player.getHungerManager().getFoodLevel();
        boolean emergency = shouldEmergencyEatNow();
        if (!emergency && foodLevel >= (int) hungerThreshold.get()) return;
        if (mc.player.isUsingItem()) return;

        long now = System.currentTimeMillis();
        if (now - lastEatTime < (long) (interval.get() * 1000)) return;

        int foodSlot = findFoodInHotbar(emergency, foodLevel);
        if (foodSlot == -1) return;

        startEating(foodSlot, foodLevel, emergency, now);
    }

    private void startEating(int foodSlot, int foodLevel, boolean emergency, long now) {
        if (mc.player == null) {
            return;
        }

        savedSlot = mc.player.getInventory().getSelectedSlot();
        eatingSlot = foodSlot;
        mc.player.getInventory().setSelectedSlot(foodSlot);
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(foodSlot));
        }

        ItemStack stack = mc.player.getInventory().getStack(foodSlot);
        startingFoodLevel = foodLevel;
        startingStackCount = stack.getCount();
        startingCombinedHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        retryAttempts = 0;
        emergencyEating = emergency && isEmergencyFood(stack);
        if (!mc.options.useKey.isPressed()) {
            mc.options.useKey.setPressed(true);
            forcedUseKey = true;
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        isEating = true;
        eatTicks = 0;
        lastEatTime = now;
    }

    private void tickEating() {
        if (mc.player == null || eatingSlot < 0 || eatingSlot > 8) {
            stopEating();
            return;
        }

        ItemStack stack = mc.player.getInventory().getStack(eatingSlot);
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (stack.isEmpty() || food == null) {
            stopEating();
            return;
        }

        if (mc.player.getInventory().getSelectedSlot() != eatingSlot) {
            mc.player.getInventory().setSelectedSlot(eatingSlot);
            if (mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(eatingSlot));
            }
        }

        if (!mc.options.useKey.isPressed()) {
            mc.options.useKey.setPressed(true);
            forcedUseKey = true;
        }

        eatTicks++;
        boolean hungerIncreased = mc.player.getHungerManager().getFoodLevel() > startingFoodLevel;
        boolean consumedItem = stack.getCount() < startingStackCount;
        boolean healthImproved = mc.player.getHealth() + mc.player.getAbsorptionAmount() > startingCombinedHealth + 0.45F;

        if (mc.player.isUsingItem()) {
            if (eatTicks > 72) {
                stopEating();
            }
            return;
        }

        if (hungerIncreased || consumedItem || (emergencyEating && healthImproved)) {
            stopEating();
            return;
        }

        if (eatTicks <= 24 && eatTicks % 2 == 1) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            retryAttempts++;
            return;
        }

        if (eatTicks > 36 || retryAttempts > 8) {
            stopEating();
        }
    }

    private void stopEating() {
        if (mc.player != null && mc.interactionManager != null && mc.player.isUsingItem()) {
            mc.interactionManager.stopUsingItem(mc.player);
        }
        if (forcedUseKey && mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
        isEating = false;
        eatTicks = 0;
        retryAttempts = 0;
        startingFoodLevel = 0;
        startingStackCount = 0;
        startingCombinedHealth = 0.0F;
        eatingSlot = -1;
        forcedUseKey = false;
        emergencyEating = false;
        if (savedSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(savedSlot);
            if (mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(savedSlot));
            }
            savedSlot = -1;
        }
    }

    private int findFoodInHotbar(boolean emergency, int foodLevel) {
        if (mc.player == null) return -1;

        String filter = foodFilter.get().trim().toLowerCase();
        boolean hasFilter = !filter.isEmpty();
        String[] filterWords = hasFilter ? filter.split("[,;\\s]+") : new String[0];
        boolean requireFilter = hasFilter && onlyFiltered.get();

        int preferredSlot = -1;
        double preferredScore = Double.NEGATIVE_INFINITY;
        int fallbackSlot = -1;
        double fallbackScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food == null) continue;
            if (emergency && !isEmergencyFood(stack)) continue;
            if (!emergency && foodLevel >= 20) continue;

            boolean matches = !hasFilter || matchesFilter(stack, filterWords);
            double score = scoreFood(stack, food, emergency, foodLevel, matches);

            if (matches && score > preferredScore) {
                preferredScore = score;
                preferredSlot = i;
            }
            if (score > fallbackScore) {
                fallbackScore = score;
                fallbackSlot = i;
            }
        }

        if (preferredSlot != -1) {
            return preferredSlot;
        }
        return requireFilter ? -1 : fallbackSlot;
    }

    private boolean shouldEmergencyEatNow() {
        if (mc.player == null) {
            return false;
        }

        float combinedHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        return combinedHealth <= EMERGENCY_HEALTH_THRESHOLD && hasEmergencyFoodInHotbar();
    }

    private boolean hasEmergencyFoodInHotbar() {
        if (mc.player == null) {
            return false;
        }

        for (int i = 0; i < 9; i++) {
            if (isEmergencyFood(mc.player.getInventory().getStack(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isEmergencyFood(ItemStack stack) {
        return !stack.isEmpty() && (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE));
    }

    private boolean matchesFilter(ItemStack stack, String[] filterWords) {
        String itemName = stack.getName().getString().toLowerCase();
        for (String word : filterWords) {
            if (!word.isEmpty() && itemName.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private double scoreFood(ItemStack stack, FoodComponent food, boolean emergency, int foodLevel, boolean filterMatch) {
        double score = food.nutrition() * 3.0D + stack.getCount() * 0.08D;
        if (filterMatch) {
            score += 120.0D;
        }
        if (foodLevel <= 6) {
            score += food.nutrition() * 1.6D;
        }
        if (isEmergencyFood(stack)) {
            score += emergency ? 1000.0D : -18.0D;
        }
        return score;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        lastEatTime = 0;
        isEating = false;
        savedSlot = -1;
        eatingSlot = -1;
        eatTicks = 0;
        retryAttempts = 0;
        forcedUseKey = false;
        emergencyEating = false;
        startingCombinedHealth = 0.0F;
    }

    @Override
    public void onDisable() {
        if (isEating) {
            stopEating();
        }
        super.onDisable();
    }
}
