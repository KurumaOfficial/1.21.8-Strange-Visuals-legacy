package ru.strange.client.module.impl.utilities;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.mixin.accessor.ClientWorldAccessor;
import ru.strange.client.mixin.accessor.LivingEntityAccessor;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@IModule(
        name = "Фейк игрок",
        description = "Спавнит клиентского фейк-игрока для тренировки PvP",
        category = Category.Utilities,
        bind = -1
)
public class FakePlayer extends Module {

    public static final String TAG_FAKE_PLAYER = "strange_fake_player";

    private static int generateEntityId() {
        return ThreadLocalRandom.current().nextInt(900_000, 1_900_000);
    }

    private final BooleanSetting infiniteTotems = new BooleanSetting("Бесконечный тотем", true);
    private final BooleanSetting copyEquipment  = new BooleanSetting("Копировать броню", true);

    private FakePlayerEntity fake;
    private UUID             fakeUUID;
    private long             lastAttackTime = 0;

    // состояние "умер" — ждём анимации, потом пересоздаём
    private boolean isDead          = false;
    private int     deathTicksLeft  = 0;
    private static final int DEATH_ANIM_TICKS = 20; // тиков до удаления трупа

    public FakePlayer() {
        addSettings(infiniteTotems, copyEquipment);
    }

    // ═══════════════════════════════════════════ lifecycle ══════════

    @Override
    public void onEnable() {
        super.onEnable();
        isDead = false;
        deathTicksLeft = 0;
        spawn();
    }

    @Override
    public void onDisable() {
        despawn();
        isDead = false;
        deathTicksLeft = 0;
        super.onDisable();
    }

    // ═══════════════════════════════════════════ events ══════════════

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null) return;

        // ── обработка смерти ──────────────────────────────────────────
        if (isDead) {
            if (deathTicksLeft > 0) {
                deathTicksLeft--;

                // крутим тело вниз (анимация смерти)
                if (fake != null && !fake.isRemoved()) {
                    float deathProgress = 1f - deathTicksLeft / (float) DEATH_ANIM_TICKS;
                    fake.deathTime = (int)(deathProgress * 20);
                }
                return;
            }

            // анимация закончилась — полностью удаляем труп и пересоздаём
            fullyRemove();
            isDead = false;
            deathTicksLeft = 0;
            spawn();
            return;
        }

        // ── обычный апдейт ────────────────────────────────────────────
        if (fake == null || fake.isRemoved()) {
            spawn();
            return;
        }

        if (copyEquipment.get()) {
            syncEquipment();
        }

        if (infiniteTotems.get()) {
            fake.setStackInHand(Hand.OFF_HAND, Items.TOTEM_OF_UNDYING.getDefaultStack());
        }

        fake.setNoGravity(true);
        fake.setVelocity(fake.getVelocity().multiply(0.6, 0.98, 0.6));
        fake.limbAnimator.setSpeed(0.0f);
    }

    @EventInit(value = ru.strange.client.event.Priority.HIGHEST)
    public void onAttack(EventAttack event) {
        if (!enable) return;
        if (!event.isAttempt()) return;
        if (mc.player == null || mc.world == null) return;
        if (fake == null) return;
        if (event.getTarget() == null) return;
        if (event.getTarget() != fake) return;
        if (isDead) return;

        event.cancel();

        long now        = System.currentTimeMillis();
        float attackSpeed = getAttackSpeed();
        float cooldownMs  = 1000.0f / Math.max(0.05f, attackSpeed);
        float strength    = MathHelper.clamp(((float)(now - lastAttackTime)) / cooldownMs, 0f, 1f);
        lastAttackTime    = now;

        boolean fullStrength = strength >= 0.95f;
        boolean crit         = fullStrength && isCrit(mc.player);
        boolean sweeping     = fullStrength && mc.player.isSprinting();

        float damage = calculateLocalDamage(mc.player, strength);
        applyLocalDamage(damage, crit, sweeping, fullStrength);
    }

    // ═══════════════════════════════════════════ spawn/despawn ════════

    private void spawn() {
        if (mc.player == null || mc.world == null) return;

        despawn();

        fakeUUID = UUID.randomUUID();
        GameProfile profile = new GameProfile(fakeUUID, mc.player.getName().getString());

        try {
            profile.getProperties().putAll(mc.player.getGameProfile().getProperties());
        } catch (Exception e) {
            Strange.LOGGER.warn("Не удалось скопировать свойства профиля для фейк-игрока", e);
        }

        int entityId = generateEntityId();
        fake = new FakePlayerEntity((ClientWorld) mc.world, profile, entityId);
        fake.addCommandTag(TAG_FAKE_PLAYER);

        fake.copyPositionAndRotation(mc.player);
        fake.setHeadYaw(mc.player.getHeadYaw());
        fake.setBodyYaw(mc.player.getBodyYaw());

        fake.setVelocity(0, 0, 0);
        fake.setNoGravity(true);
        fake.setSilent(false);
        fake.setHealth(20.0f);
        fake.deathTime = 0;

        try {
            var inst = fake.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (inst != null) inst.setBaseValue(20.0);
        } catch (Exception e) {
            Strange.LOGGER.warn("Не удалось установить MAX_HEALTH для фейк-игрока", e);
        }

        if (copyEquipment.get()) syncEquipment();
        if (infiniteTotems.get()) {
            fake.setStackInHand(Hand.OFF_HAND, Items.TOTEM_OF_UNDYING.getDefaultStack());
        }

        fake.spawn();
    }

    /** Убирает из мира и обнуляет поле. */
    private void despawn() {
        if (fake == null) return;
        try { fake.remove(); } catch (Exception ignored) {}
        fake = null;
    }

    /** Полное удаление: через accessor + setRemoved, чтобы хитбокс точно ушёл. */
    private void fullyRemove() {
        if (fake == null) return;
        try {
            if (fake.getWorld() instanceof ClientWorldAccessor accessor) {
                accessor.invokeRemoveEntity(fake.getId(), Entity.RemovalReason.KILLED);
            }
            fake.setRemoved(Entity.RemovalReason.KILLED);
        } catch (Exception e) {
            try { fake.remove(); } catch (Exception ignored) {}
        }
        fake = null;
    }

    // ═══════════════════════════════════════════ equipment ════════════

    private void syncEquipment() {
        if (mc.player == null || fake == null) return;
        fake.equipStack(EquipmentSlot.HEAD,  mc.player.getEquippedStack(EquipmentSlot.HEAD).copy());
        fake.equipStack(EquipmentSlot.CHEST, mc.player.getEquippedStack(EquipmentSlot.CHEST).copy());
        fake.equipStack(EquipmentSlot.LEGS,  mc.player.getEquippedStack(EquipmentSlot.LEGS).copy());
        fake.equipStack(EquipmentSlot.FEET,  mc.player.getEquippedStack(EquipmentSlot.FEET).copy());
        fake.setStackInHand(Hand.MAIN_HAND,  mc.player.getMainHandStack().copy());
        if (!infiniteTotems.get()) {
            fake.setStackInHand(Hand.OFF_HAND, mc.player.getOffHandStack().copy());
        }
    }

    // ═══════════════════════════════════════════ combat helpers ═══════

    private float getAttackSpeed() {
        if (mc.player == null) return 4.0f;
        try { return (float) mc.player.getAttributeValue(EntityAttributes.ATTACK_SPEED); }
        catch (Exception e) { return 4.0f; }
    }

    private float calculateLocalDamage(PlayerEntity player, float strength) {
        float base;
        try { base = (float) player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE); }
        catch (Exception e) { base = 1.0f; }
        return Math.max(0.1f, base * (0.2f + strength * strength * 0.8f));
    }

    private boolean isCrit(PlayerEntity player) {
        return !player.isOnGround()
                && !player.isTouchingWater()
                && !player.isSubmergedIn(FluidTags.WATER)
                && !player.isClimbing()
                && !player.hasVehicle()
                && !player.isSprinting();
    }

    // ═══════════════════════════════════════════ damage ═══════════════

    private void applyLocalDamage(float amount, boolean crit, boolean sweeping, boolean fullStrength) {
        if (fake == null || mc.world == null || mc.player == null) return;

        // звуки
        if (crit)                        playSoundAt(fake, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT);
        else if (fullStrength && sweeping) playSoundAt(fake, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP);
        else if (fullStrength)             playSoundAt(fake, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG);
        else                               playSoundAt(fake, SoundEvents.ENTITY_PLAYER_ATTACK_WEAK);

        if (crit)     { spawnCritParticles(); amount *= 1.5f; }
        if (sweeping)   spawnSweepParticles();

        showHitFeedback();

        float newHealth = fake.getHealth() - amount;

        if (newHealth > 0f) {
            fake.setHealth(newHealth);
            return;
        }

        // ── смерть ────────────────────────────────────────────────────
        ItemStack offhand  = fake.getOffHandStack();
        ItemStack mainhand = fake.getMainHandStack();
        boolean hasTotem   = (offhand  != null && offhand.getItem()  == Items.TOTEM_OF_UNDYING)
                || (mainhand != null && mainhand.getItem() == Items.TOTEM_OF_UNDYING);

        if (hasTotem || infiniteTotems.get()) {
            popTotem();
            return;
        }

        killFake();
    }

    /**
     * Настоящая смерть: звук, фиксируем здоровье = 0,
     * запускаем таймер анимации — через DEATH_ANIM_TICKS тиков
     * onUpdate() сам удалит и пересоздаст.
     */
    private void killFake() {
        if (fake == null) return;

        fake.setHealth(0f);
        playDeathSound();
        spawnDeathParticles();

        // флаг — не трогаем в onAttack, ждём конца анимации
        isDead         = true;
        deathTicksLeft = DEATH_ANIM_TICKS;
    }

    private void showHitFeedback() {
        if (fake == null || mc.world == null || mc.player == null) return;
        playSoundAt(fake, SoundEvents.ENTITY_PLAYER_HURT);
        setHurtTime(fake, 10);
        fake.animateDamage(mc.player.getYaw());
        applyKnockback();
    }

    private void popTotem() {
        if (fake == null || mc.world == null || mc.player == null) return;

        if (!infiniteTotems.get()) {
            ItemStack off = fake.getOffHandStack();
            if (off != null && off.getItem() == Items.TOTEM_OF_UNDYING) {
                fake.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            } else {
                ItemStack main = fake.getMainHandStack();
                if (main != null && main.getItem() == Items.TOTEM_OF_UNDYING) {
                    fake.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                }
            }
        }

        playSoundAt(fake, SoundEvents.ITEM_TOTEM_USE);

        if (mc.player.networkHandler != null) {
            try {
                new EntityStatusS2CPacket(fake, (byte) 35).apply(mc.player.networkHandler);
            } catch (Exception e) {
                Strange.LOGGER.warn("Не удалось применить анимацию тотема", e);
            }
        }

        fake.setHealth(20.0f);
        fake.deathTime = 0;
        fake.clearStatusEffects();
        fake.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,  900, 1));
        fake.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION,    100, 1));
        fake.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE,800, 0));

        if (infiniteTotems.get()) {
            fake.setStackInHand(Hand.OFF_HAND, Items.TOTEM_OF_UNDYING.getDefaultStack());
        }

        setHurtTime(fake, 10);
    }

    // ═══════════════════════════════════════════ particles / sounds ═══

    private void spawnCritParticles() {
        if (fake == null || mc.world == null) return;
        for (int i = 0; i < 20; i++) {
            double ox = (mc.world.random.nextDouble() - 0.5) * 0.5;
            double oy = mc.world.random.nextDouble() * fake.getHeight();
            double oz = (mc.world.random.nextDouble() - 0.5) * 0.5;
            mc.world.addParticleClient(ParticleTypes.CRIT,
                    fake.getX() + ox, fake.getY() + oy, fake.getZ() + oz,
                    (mc.world.random.nextDouble() - 0.5) * 0.2,
                    mc.world.random.nextDouble() * 0.2,
                    (mc.world.random.nextDouble() - 0.5) * 0.2);
        }
    }

    private void spawnSweepParticles() {
        if (fake == null || mc.world == null) return;
        mc.world.addParticleClient(ParticleTypes.SWEEP_ATTACK,
                fake.getX(), fake.getY() + fake.getHeight() * 0.5, fake.getZ(), 0, 0, 0);
    }

    private void spawnDeathParticles() {
        if (fake == null || mc.world == null) return;
        for (int i = 0; i < 30; i++) {
            double ox = (mc.world.random.nextDouble() - 0.5) * 0.6;
            double oy = mc.world.random.nextDouble() * fake.getHeight();
            double oz = (mc.world.random.nextDouble() - 0.5) * 0.6;
            mc.world.addParticleClient(ParticleTypes.DAMAGE_INDICATOR,
                    fake.getX() + ox, fake.getY() + oy, fake.getZ() + oz,
                    (mc.world.random.nextDouble() - 0.5) * 0.1,
                    mc.world.random.nextDouble() * 0.1,
                    (mc.world.random.nextDouble() - 0.5) * 0.1);
        }
    }

    private void applyKnockback() {
        if (fake == null || mc.player == null) return;
        Vec3d dir = fake.getPos().subtract(mc.player.getPos()).normalize();
        double kb = 0.4 + (mc.player.isSprinting() ? 0.2 : 0);
        fake.setVelocity(fake.getVelocity().add(dir.x * kb, 0.1, dir.z * kb));
    }

    private void playSoundAt(Entity entity, net.minecraft.sound.SoundEvent sound) {
        if (mc.player == null || mc.world == null || entity == null || sound == null) return;
        mc.world.playSoundFromEntity(mc.player, entity, sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void playDeathSound() {
        playSoundAt(fake, SoundEvents.ENTITY_PLAYER_DEATH);
    }

    private void setHurtTime(Entity entity, int ticks) {
        if (entity == null || ticks < 1) return;
        if (entity instanceof LivingEntityAccessor accessor) {
            accessor.setHurtTime(ticks);
            accessor.setMaxHurtTime(ticks);
        }
    }

    // ═══════════════════════════════════════════ inner entity ═════════

    private static class FakePlayerEntity extends OtherClientPlayerEntity {

        private final int forcedId;

        public FakePlayerEntity(ClientWorld world, GameProfile profile, int forcedId) {
            super(world, profile);
            this.forcedId = forcedId;
        }

        public void spawn() {
            this.unsetRemoved();
            this.setId(forcedId);
            if (this.getWorld() instanceof ClientWorldAccessor accessor) {
                accessor.invokeAddEntity(this);
            }
        }

        /** Удаляет из мира через accessor (RemovalReason.DISCARDED). */
        public void remove() {
            if (this.getWorld() instanceof ClientWorldAccessor accessor) {
                accessor.invokeRemoveEntity(this.getId(), Entity.RemovalReason.DISCARDED);
            }
            this.setRemoved(Entity.RemovalReason.DISCARDED);
        }

        @Override
        public void takeKnockback(double strength, double x, double z) {
            // своя реализация выше
        }
    }
}
