package ru.strange.client.entity.pet;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A pet axolotl that is only visible to the player who summoned it.
 */
public class AxolotlPetEntity extends net.minecraft.entity.passive.AxolotlEntity {
    private PlayerEntity owner;
    private int followDelay = 0;

    public AxolotlPetEntity(EntityType<? extends net.minecraft.entity.passive.AxolotlEntity> entityType, World world) {
        super(entityType, world);
    }

    public AxolotlPetEntity(World world, PlayerEntity owner) {
        super(EntityType.AXOLOTL, world);
        this.owner = owner;
        this.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), owner.getPitch());
    }

    @Override
    public void tick() {
        super.tick();
        if (owner == null) {
            this.discard();
            return;
        }

        // Only update if we're the client player
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player != owner) {
            // Not visible to others
            return;
        }

        // Increase delay to make movement less jittery
        followDelay++;
        if (followDelay < 5) {
            return;
        }
        followDelay = 0;

        double dx = owner.getX() - this.getX();
        double dy = owner.getY() - this.getY();
        double dz = owner.getZ() - this.getZ();
        double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

        if (distance > 2.0) {
            // Simple follow behavior
            double moveX = dx / distance * 0.1;
            double moveZ = dz / distance * 0.1;
            this.move(MovementType.SELF, new Vec3d(moveX, 0, moveZ));
            this.setVelocity(moveX, this.getVelocity().y, moveZ);
        }
    }

    @Override
    public boolean isInvisible() {
        // Only invisible to other players, visible to owner
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null) return super.isInvisible();
        return client.player != owner && super.isInvisible();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }
}