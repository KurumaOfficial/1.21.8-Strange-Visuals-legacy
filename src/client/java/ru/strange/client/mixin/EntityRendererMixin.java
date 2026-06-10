package ru.strange.client.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityHitbox;
import net.minecraft.client.render.entity.state.EntityHitboxAndView;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.Strange;
import ru.strange.client.module.impl.player.FakeHitboxes;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Shadow
    protected abstract void appendHitboxes(T entity, ImmutableList.Builder<EntityHitbox> builder, float tickProgress);

    @Inject(method = "createHitbox", at = @At("HEAD"), cancellable = true)
    private void strange$replaceDebugHitbox(T entity, float tickProgress, boolean green, CallbackInfoReturnable<EntityHitboxAndView> cir) {
        if (strange$shouldHideVanillaHitbox(entity)) {
            cir.setReturnValue(new EntityHitboxAndView(0.0, 0.0, 0.0, ImmutableList.of()));
            return;
        }

        if (!FakeHitboxes.shouldOverrideDebugHitbox(entity)) {
            return;
        }

        Box box = FakeHitboxes.getDebugBoundingBox(entity);
        float[] color = FakeHitboxes.getDebugHitboxColor(entity, green);
        ImmutableList.Builder<EntityHitbox> builder = new ImmutableList.Builder<>();

        builder.add(new EntityHitbox(
                box.minX - entity.getX(),
                box.minY - entity.getY(),
                box.minZ - entity.getZ(),
                box.maxX - entity.getX(),
                box.maxY - entity.getY(),
                box.maxZ - entity.getZ(),
                color[0],
                color[1],
                color[2]
        ));

        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            float halfWidth = Math.min(vehicle.getWidth(), entity.getWidth()) / 2.0F;
            Vec3d offset = vehicle.getPassengerRidingPos(entity).subtract(entity.getPos());
            builder.add(new EntityHitbox(
                    offset.x - halfWidth,
                    offset.y,
                    offset.z - halfWidth,
                    offset.x + halfWidth,
                    offset.y + 0.0625D,
                    offset.z + halfWidth,
                    1.0F,
                    1.0F,
                    0.0F
            ));
        }

        appendHitboxes(entity, builder, tickProgress);

        Vec3d rotation = entity.getRotationVec(tickProgress);
        cir.setReturnValue(new EntityHitboxAndView(rotation.x, rotation.y, rotation.z, builder.build()));
    }

    private static boolean strange$shouldHideVanillaHitbox(Entity entity) {
        if (entity == null || Strange.get == null || Strange.get.manager == null) return false;
        ru.strange.client.module.impl.player.Box box = Strange.get.manager.get(ru.strange.client.module.impl.player.Box.class);
        return box != null && box.enable;
    }
}
