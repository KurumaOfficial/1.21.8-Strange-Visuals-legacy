package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("attackCooldown")
    void setAttackCooldown(int cooldown);

    @Accessor("attackCooldown")
    int getAttackCooldown();

    @Accessor("itemUseCooldown")
    void setItemUseCooldown(int cooldown);

    @Accessor("itemUseCooldown")
    int getItemUseCooldown();

    @Mutable
    @Accessor("session")
    void setSession(net.minecraft.client.session.Session session);
}
