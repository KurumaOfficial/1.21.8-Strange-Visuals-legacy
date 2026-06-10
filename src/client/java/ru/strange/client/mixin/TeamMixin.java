package ru.strange.client.mixin;

import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.module.impl.other.NameProtect;

@Mixin(Team.class)
public class TeamMixin {

    @Inject(method = "decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;", at = @At("RETURN"), cancellable = true)
    private static void strange$protectDecoratedName(AbstractTeam team, Text name, CallbackInfoReturnable<MutableText> cir) {
        if (!NameProtect.isActive()) {
            return;
        }

        Text processed = NameProtect.process(cir.getReturnValue());
        if (processed != null) {
            cir.setReturnValue(processed.copy());
        }
    }
}