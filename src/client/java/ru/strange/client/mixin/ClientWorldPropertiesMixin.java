package ru.strange.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.strange.client.module.impl.other.TimeSet;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.Properties.class)
public abstract class ClientWorldPropertiesMixin {
    @ModifyReturnValue(method = "getTimeOfDay", at = @At("RETURN"))
    private long hookGetTime(long original) {
        return TimeSet.customTime;
    }
}