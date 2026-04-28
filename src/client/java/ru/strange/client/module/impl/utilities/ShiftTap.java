package ru.strange.client.module.impl.utilities;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "РЁРёС„С‚ С‚Р°Рї",
        description = "РђРІС‚РѕРјР°С‚РёС‡РµСЃРєРѕРµ РїСЂРёСЃРµРґР°РЅРёРµ РїСЂРё Р°С‚Р°РєРµ РґР»СЏ РєСЂРёС‚Р°",
        category = Category.Utilities,
        bind = -1
)
public class ShiftTap extends Module {

    private final SliderSetting holdTicks = new SliderSetting("РўРёРєРё С€РёС„С‚Р°", 3, 1, 8, 1, false);

    private int sneakTicksLeft = 0;
    private boolean forcedSneak = false;
    private boolean manualSneakBeforeForce = false;

    public ShiftTap() {
        addSettings(holdTicks);
    }

    @EventInit(value = ru.strange.client.event.Priority.HIGHEST)
    public void onAttack(EventAttack event) {
        if (!enable || mc.player == null || mc.world == null) return;
        if (!event.isAttempt()) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        PlayerEntity self = mc.player;
        if (shouldShiftTap(self)) {
            startShiftTap();
        }
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null) {
            releaseShiftTap();
            return;
        }

        if (sneakTicksLeft > 0) {
            mc.options.sneakKey.setPressed(true);
            forcedSneak = true;
            sneakTicksLeft--;
        } else if (forcedSneak) {
            releaseShiftTap();
        }
    }

    @Override
    public void onDisable() {
        releaseShiftTap();
        super.onDisable();
    }

    private void startShiftTap() {
        if (mc.player == null || mc.options == null) return;

        sneakTicksLeft = Math.max(1, (int) holdTicks.get());
        manualSneakBeforeForce = mc.options.sneakKey.isPressed();
        mc.options.sneakKey.setPressed(true);
        forcedSneak = true;
        mc.player.setSprinting(false);

        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }
    }

    private void releaseShiftTap() {
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(manualSneakBeforeForce);
        }

        forcedSneak = false;
        sneakTicksLeft = 0;
        manualSneakBeforeForce = false;
    }

    private boolean shouldShiftTap(PlayerEntity player) {
        if (!player.isSprinting()) return false;
        if (player.isSneaking()) return false;
        if (player.isSubmergedInWater() || player.isTouchingWater()) return false;
        if (player.isClimbing()) return false;
        if (player.hasVehicle()) return false;
        return true;
    }
}
