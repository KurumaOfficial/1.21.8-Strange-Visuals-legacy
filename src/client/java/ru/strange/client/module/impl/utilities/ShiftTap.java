package ru.strange.client.module.impl.utilities;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Шифт тап",
        description = "",
        category = Category.Utilities,
        bind = -1
)
public class ShiftTap extends Module {

    private final SliderSetting holdTicks = new SliderSetting("Тики шифта", 3, 1, 8, 1, false);

    private int sneakTicksLeft = 0;
    private boolean forcedSneak = false;

    public ShiftTap() {
        addSettings(holdTicks);
    }

    @EventInit
    public void onAttack(EventAttack event) {
        if (!enable || mc.player == null || mc.world == null) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        PlayerEntity self = mc.player;
        if (self == null) return;

        if (canCrit(self)) {
            sneakTicksLeft = (int) holdTicks.get();
        }
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null) return;

        if (sneakTicksLeft > 0) {
            mc.options.sneakKey.setPressed(true);
            forcedSneak = true;
            sneakTicksLeft--;
        } else if (forcedSneak) {
            mc.options.sneakKey.setPressed(false);
            forcedSneak = false;
        }
    }

    @Override
    public void onDisable() {
        if (forcedSneak && mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }

        forcedSneak = false;
        sneakTicksLeft = 0;
        super.onDisable();
    }

    private boolean canCrit(PlayerEntity player) {
        if (player.isOnGround()) return false;
        if (player.isSubmergedInWater() || player.isTouchingWater()) return false;
        if (player.isClimbing()) return false;
        if (player.hasVehicle()) return false;
        if (player.isSprinting()) return false;
        return true;
    }
}