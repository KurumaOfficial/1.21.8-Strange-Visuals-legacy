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
        name = "Шифт тап",
        description = "Автоматическое приседание при атаке для крита",
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
        if (!event.isAttempt()) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        PlayerEntity self = mc.player;
        if (self == null) return;

        // Шифт-тап нужен когда игрок бежит (спринт) и атакует —
        // присесть на мгновение чтобы сбросить спринт и получить крит
        if (shouldShiftTap(self)) {
            startShiftTap();
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
        mc.options.sneakKey.setPressed(true);
        forcedSneak = true;
        mc.player.setSprinting(false);

        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }
    }

    private void releaseShiftTap() {
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }

        forcedSneak = false;
        sneakTicksLeft = 0;
    }

    /**
     * Определяет, нужен ли шифт-тап для получения крита.
     * Шифт-тап полезен когда игрок спринтит (и поэтому не может получить крит),
     * находится в воздухе и не в воде — краткое приседание сбросит спринт.
     */
    private boolean shouldShiftTap(PlayerEntity player) {
        // Шифт-тап нужен только если игрок спринтит (спринт мешает криту)
        if (!player.isSprinting()) return false;
        // Нельзя критовать на земле
        if (player.isOnGround()) return false;
        // Нельзя критовать в воде
        if (player.isSubmergedInWater() || player.isTouchingWater()) return false;
        // Нельзя критовать на лестнице
        if (player.isClimbing()) return false;
        // Нельзя критовать верхом
        if (player.hasVehicle()) return false;
        return true;
    }
}
