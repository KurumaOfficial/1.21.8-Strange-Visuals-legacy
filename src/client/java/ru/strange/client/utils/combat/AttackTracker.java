package ru.strange.client.utils.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.mixin.accessor.LivingEntityAccessor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class AttackTracker {
    private static final long CONFIRMATION_TIMEOUT_MS = 300L;
    private static final AttackTracker INSTANCE = new AttackTracker();

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Map<Integer, PendingAttack> pendingAttacks = new HashMap<>();

    private AttackTracker() {
    }

    public static AttackTracker getInstance() {
        return INSTANCE;
    }

    public void trackExecutedAttack(Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return;
        }

        if (mc.world == null || target.isRemoved()) {
            return;
        }

        int hurtTime = living instanceof LivingEntityAccessor accessor ? accessor.getHurtTime() : 0;
        float cooldownProgress = mc.player != null ? mc.player.getAttackCooldownProgress(0.5f) : 0.0f;
        boolean critical = mc.player != null && CombatUtil.isCriticalAttack(mc.player, living, cooldownProgress);
        boolean sweeping = mc.player != null
                && cooldownProgress > 0.9f
                && mc.player.isOnGround()
                && mc.player.isSprinting();

        pendingAttacks.put(target.getId(), new PendingAttack(
                target.getId(),
                hurtTime,
                living.getHealth(),
                cooldownProgress,
                critical,
                sweeping,
                System.currentTimeMillis() + CONFIRMATION_TIMEOUT_MS
        ));
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (pendingAttacks.isEmpty()) {
            return;
        }

        if (mc.world == null) {
            pendingAttacks.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<PendingAttack> iterator = pendingAttacks.values().iterator();
        while (iterator.hasNext()) {
            PendingAttack pending = iterator.next();
            Entity entity = mc.world.getEntityById(pending.entityId());
            if (!(entity instanceof LivingEntity living)) {
                iterator.remove();
                continue;
            }

            int hurtTime = living instanceof LivingEntityAccessor accessor ? accessor.getHurtTime() : 0;
            float healthAfter = living.getHealth();
            boolean healthDropped = living.getHealth() + 0.001f < pending.healthBefore();
            boolean confirmed = hurtTime > pending.hurtTimeBefore()
                    || healthDropped
                    || !living.isAlive()
                    || living.deathTime > 0;

            if (confirmed) {
                boolean killed = !living.isAlive() || living.deathTime > 0;
                boolean damaging = healthDropped || killed;
                EventManager.call(EventAttack.confirmed(
                        living,
                        pending.healthBefore(),
                        healthAfter,
                        pending.cooldownProgress(),
                        pending.critical(),
                        pending.sweeping(),
                        damaging,
                        killed
                ));
                iterator.remove();
                continue;
            }

            if (now > pending.expiresAtMs()) {
                iterator.remove();
            }
        }
    }

    @EventInit
    public void onWorldChange(EventChangeWorld event) {
        pendingAttacks.clear();
    }

    private record PendingAttack(int entityId, int hurtTimeBefore, float healthBefore, float cooldownProgress,
                                 boolean critical, boolean sweeping, long expiresAtMs) {
    }
}
