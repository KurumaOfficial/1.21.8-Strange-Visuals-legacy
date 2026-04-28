package ru.strange.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.x150.renderer.fontng.FontScalingRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.Strange;
import ru.strange.client.StarterMenu.StrangeVisualsClient;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.module.impl.other.NameProtect;
import ru.strange.client.utils.other.ItemShaderProfiles;
import ru.strange.client.utils.other.ServerUtil;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow
    private IntegratedServer server;

    @ModifyVariable(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"), argsOnly = true)
    private Screen strange$wrapTitleScreen(Screen screen) {
        return StrangeVisualsClient.wrapTitleScreenIfNeeded((MinecraftClient) (Object) this, screen);
    }

    @Inject(method = "onResolutionChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;setScaleFactor(I)V"))
    void preSetScaleFactor(CallbackInfo ci, @Local(ordinal = 0) int i) {
        FontScalingRegistry.resize(i);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"))
    private void strange$ensureIntegratedServerShutdown(Screen screen, boolean transferring, CallbackInfo ci) {
        ServerUtil.invalidateCache();
        if (server != null && !server.isStopping()) {
            server.stop(false);
        }
    }

    @Inject(method = "joinWorld", at = @At("TAIL"))
    public void loadWorld(CallbackInfo ci) {
        ServerUtil.invalidateCache();
        EventManager.call(new EventChangeWorld());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void strange$runtimeMaintenance(CallbackInfo ci) {
        if (Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.tickAutoSave();
        }
        ItemShaderProfiles.tick();
    }

    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void strange$protectWindowTitle(CallbackInfoReturnable<String> cir) {
        String title = cir.getReturnValue();
        if (title != null) {
            String processed = NameProtect.process(title);
            if (processed != null && !processed.equals(title)) {
                cir.setReturnValue(processed);
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void strange$flushConfigOnClose(CallbackInfo ci) {
        if (Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.flushAutoSave();
        }
    }
}
