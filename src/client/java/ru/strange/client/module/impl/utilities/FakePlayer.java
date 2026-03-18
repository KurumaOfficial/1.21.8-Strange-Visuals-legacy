package ru.strange.client.module.impl.utilities;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

import java.lang.reflect.Method;
import java.util.UUID;

@IModule(
        name = "Фейк игрок",
        description = "",
        category = Category.Utilities,
        bind = -1
)
public class FakePlayer extends Module {

    public static final String TAG_FAKE_PLAYER = "strange_fake_player";
    private static final int LOCAL_FAKE_ID = -13371337;

    private final BooleanSetting infiniteTotems = new BooleanSetting("Бесконечный тотем", true);
    private final BooleanSetting copyEquipment = new BooleanSetting("Копировать броню", true);
    private final BooleanSetting hittable = new BooleanSetting("Можно бить", true);
    private final SliderSetting health = new SliderSetting("ХП", 20, 1, 40, 1, false);

    private OtherClientPlayerEntity fake;

    public FakePlayer() {
        addSettings(infiniteTotems, copyEquipment, hittable, health);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        spawn();
    }

    @Override
    public void onDisable() {
        despawn();
        super.onDisable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null) return;

        if (fake == null || fake.isRemoved()) {
            spawn();
            return;
        }

        if (copyEquipment.get()) {
            syncEquipment();
        }

        if (infiniteTotems.get()) {
            fake.equipStack(EquipmentSlot.OFFHAND, Items.TOTEM_OF_UNDYING.getDefaultStack());
        }

        fake.setVelocity(fake.getVelocity().multiply(0.6));
    }

    @EventInit
    public void onAttack(EventAttack event) {
        if (!enable || !hittable.get()) return;
        if (mc.player == null || mc.world == null) return;
        if (fake == null) return;
        if (event.getTarget() == null) return;
        if (event.getTarget() != fake) return;

        try {
            event.cancel();
        } catch (Throwable ignored) {
        }

        float damage = calculateLocalDamage(mc.player);
        boolean crit = isCrit(mc.player);

        applyLocalDamage(damage, crit);
    }

    private void spawn() {
        if (mc.player == null || mc.world == null) return;

        despawn();

        GameProfile profile = new GameProfile(UUID.randomUUID(), "FakePlayer");
        try {
            profile.getProperties().putAll(mc.player.getGameProfile().getProperties());
        } catch (Throwable ignored) {
        }

        fake = new OtherClientPlayerEntity(mc.world, profile);
        fake.setId(LOCAL_FAKE_ID);

        try {
            fake.addCommandTag(TAG_FAKE_PLAYER);
        } catch (Throwable ignored) {
        }

        fake.refreshPositionAndAngles(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.getYaw(),
                mc.player.getPitch()
        );

        fake.setHeadYaw(mc.player.getHeadYaw());
        fake.setBodyYaw(mc.player.getBodyYaw());

        fake.setVelocity(0, 0, 0);
        fake.setNoGravity(true);
        fake.setSilent(false);
        fake.setHealth((float) health.get());

        if (copyEquipment.get()) {
            syncEquipment();
        }

        if (infiniteTotems.get()) {
            fake.equipStack(EquipmentSlot.OFFHAND, Items.TOTEM_OF_UNDYING.getDefaultStack());
        }

        mc.world.addEntity(fake);
    }

    private void despawn() {
        if (fake == null) return;

        if (mc.world != null) {
            try {
                mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
            } catch (Throwable t) {
                try {
                    fake.discard();
                } catch (Throwable ignored) {
                }
            }
        }

        fake = null;
    }

    private void syncEquipment() {
        if (mc.player == null || fake == null) return;

        fake.equipStack(EquipmentSlot.HEAD, mc.player.getEquippedStack(EquipmentSlot.HEAD).copy());
        fake.equipStack(EquipmentSlot.CHEST, mc.player.getEquippedStack(EquipmentSlot.CHEST).copy());
        fake.equipStack(EquipmentSlot.LEGS, mc.player.getEquippedStack(EquipmentSlot.LEGS).copy());
        fake.equipStack(EquipmentSlot.FEET, mc.player.getEquippedStack(EquipmentSlot.FEET).copy());
        fake.equipStack(EquipmentSlot.MAINHAND, mc.player.getMainHandStack().copy());

        if (!infiniteTotems.get()) {
            fake.equipStack(EquipmentSlot.OFFHAND, mc.player.getOffHandStack().copy());
        }
    }

    private float calculateLocalDamage(PlayerEntity player) {
        float baseDamage = 1.0f;

        try {
            baseDamage = (float) player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        } catch (Throwable ignored) {
        }

        float cooldown = player.getAttackCooldownProgress(0.5f);
        float damage = baseDamage * (0.2f + cooldown * cooldown * 0.8f);

        if (isCrit(player)) {
            damage *= 1.5f;
        }

        return Math.max(0.5f, damage);
    }

    private boolean isCrit(PlayerEntity player) {
        if (player.isOnGround()) return false;
        if (player.isTouchingWater() || player.isSubmergedIn(FluidTags.WATER)) return false;
        if (player.isClimbing()) return false;
        if (player.hasVehicle()) return false;
        if (player.isSprinting()) return false;
        return true;
    }

    private void applyLocalDamage(float amount, boolean crit) {
        if (fake == null) return;

        showHitFeedback(crit);

        float currentHealth = fake.getHealth();
        float newHealth = currentHealth - amount;

        if (newHealth > 0f) {
            fake.setHealth(newHealth);
            return;
        }

        if (infiniteTotems.get()) {
            popTotem();
            fake.setHealth((float) health.get());
            return;
        }

        fake.setHealth(0f);

        try {
            mc.world.playSound(
                    null,
                    fake.getX(),
                    fake.getY(),
                    fake.getZ(),
                    SoundEvents.ENTITY_PLAYER_DEATH,
                    fake.getSoundCategory(),
                    1.0f,
                    1.0f
            );
        } catch (Throwable ignored) {
        }
    }

    private void showHitFeedback(boolean crit) {
        if (fake == null || mc.world == null) return;

        try {
            fake.handleStatus((byte) 2);
        } catch (Throwable ignored) {
        }

        tryAnimateDamage();

        try {
            fake.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }

        try {
            mc.world.playSound(
                    null,
                    fake.getX(),
                    fake.getY(),
                    fake.getZ(),
                    SoundEvents.ENTITY_PLAYER_HURT,
                    fake.getSoundCategory(),
                    1.0f,
                    0.95f + (mc.world.random.nextFloat() * 0.1f)
            );
        } catch (Throwable ignored) {
        }

        try {
            Vec3d look = mc.player.getRotationVec(1.0f).multiply(0.12, 0.0, 0.12);
            fake.setVelocity(fake.getVelocity().add(look.x, 0.05, look.z));
        } catch (Throwable ignored) {
        }
    }

    private void popTotem() {
        if (fake == null || mc.world == null) return;

        try {
            fake.handleStatus((byte) 35);
        } catch (Throwable ignored) {
        }

        try {
            mc.world.playSound(
                    null,
                    fake.getX(),
                    fake.getY(),
                    fake.getZ(),
                    SoundEvents.ITEM_TOTEM_USE,
                    fake.getSoundCategory(),
                    1.0f,
                    1.0f
            );
        } catch (Throwable ignored) {
        }
    }

    private void tryAnimateDamage() {
        if (fake == null) return;

        try {
            Method animateDamage = fake.getClass().getMethod("animateDamage", float.class);
            animateDamage.invoke(fake, 0.0f);
        } catch (Throwable ignored) {
        }
    }
}