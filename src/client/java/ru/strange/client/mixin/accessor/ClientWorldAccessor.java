package ru.strange.client.mixin.accessor;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor для ClientWorld — позволяет добавлять/удалять сущности
 * без рефлексии, через Mixin @Invoker.
 *
 * NOTE (MC 1.21.8): addEntity(int, Entity) удалён; теперь ID задаётся через
 * Entity.setId(int) до вызова addEntity(Entity).
 */
@Mixin(ClientWorld.class)
public interface ClientWorldAccessor {

    /** MC 1.21.8: addEntity принимает только Entity; ID ставится заранее через setId(). */
    @Invoker("addEntity")
    void invokeAddEntity(Entity entity);

    @Invoker("removeEntity")
    void invokeRemoveEntity(int id, Entity.RemovalReason reason);
}
