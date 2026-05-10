package ru.strange.client.mixin.accessor;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor для LivingEntity — прямой доступ к hurtTime/maxHurtTime
 * без рефлексии.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("hurtTime")
    int getHurtTime();

    @Accessor("hurtTime")
    void setHurtTime(int value);

    @Accessor("maxHurtTime")
    int getMaxHurtTime();

    @Accessor("maxHurtTime")
    void setMaxHurtTime(int value);
}
