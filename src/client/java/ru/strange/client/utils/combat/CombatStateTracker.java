package ru.strange.client.utils.combat;

import net.minecraft.entity.Entity;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventTotemPop;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class CombatStateTracker {

    public enum Marker {
        HIT,
        CRIT,
        KILL,
        TOTEM
    }

    private static final long STATE_TTL_MS = 5_000L;
    private static final CombatStateTracker INSTANCE = new CombatStateTracker();

    private final Map<Integer, EntityState> states = new HashMap<>();

    private long lastHitAt;
    private long lastCritAt;
    private long lastKillAt;
    private long lastTotemAt;

    private CombatStateTracker() {
    }

    public static CombatStateTracker getInstance() {
        return INSTANCE;
    }

    @EventInit
    public void onAttack(EventAttack event) {
        if (!event.isConfirmed() || event.getTarget() == null) {
            return;
        }

        Entity target = event.getTarget();
        EntityState state = states.computeIfAbsent(target.getId(), ignored -> new EntityState());
        long now = System.currentTimeMillis();
        state.updatedAt = now;
        state.hitAt = now;
        lastHitAt = now;

        if (event.isCritical()) {
            state.critAt = now;
            lastCritAt = now;
        }

        if (event.isKilled()) {
            state.killAt = now;
            lastKillAt = now;
        }

        prune(now);
    }

    @EventInit
    public void onTotemPop(EventTotemPop event) {
        if (event.getEntity() == null) {
            return;
        }

        EntityState state = states.computeIfAbsent(event.getEntity().getId(), ignored -> new EntityState());
        long now = System.currentTimeMillis();
        state.updatedAt = now;
        state.totemAt = now;
        lastTotemAt = now;
        prune(now);
    }

    @EventInit
    public void onWorldChange(EventChangeWorld event) {
        states.clear();
        lastHitAt = 0L;
        lastCritAt = 0L;
        lastKillAt = 0L;
        lastTotemAt = 0L;
    }

    public float getEntityPulse(Entity entity, Marker marker, long fadeMs) {
        if (entity == null || fadeMs <= 0L) {
            return 0.0f;
        }

        EntityState state = states.get(entity.getId());
        if (state == null) {
            return 0.0f;
        }

        long timestamp = state.get(marker);
        return getPulse(timestamp, fadeMs);
    }

    public float getGlobalPulse(Marker marker, long fadeMs) {
        long timestamp = switch (marker) {
            case HIT -> lastHitAt;
            case CRIT -> lastCritAt;
            case KILL -> lastKillAt;
            case TOTEM -> lastTotemAt;
        };
        return getPulse(timestamp, fadeMs);
    }

    private float getPulse(long timestamp, long fadeMs) {
        if (timestamp <= 0L || fadeMs <= 0L) {
            return 0.0f;
        }

        long age = System.currentTimeMillis() - timestamp;
        if (age >= fadeMs) {
            return 0.0f;
        }

        return 1.0f - (age / (float) fadeMs);
    }

    private void prune(long now) {
        Iterator<Map.Entry<Integer, EntityState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, EntityState> entry = iterator.next();
            if (now - entry.getValue().updatedAt > STATE_TTL_MS) {
                iterator.remove();
            }
        }
    }

    private static final class EntityState {
        private long updatedAt;
        private long hitAt;
        private long critAt;
        private long killAt;
        private long totemAt;

        private long get(Marker marker) {
            return switch (marker) {
                case HIT -> hitAt;
                case CRIT -> critAt;
                case KILL -> killAt;
                case TOTEM -> totemAt;
            };
        }
    }
}
