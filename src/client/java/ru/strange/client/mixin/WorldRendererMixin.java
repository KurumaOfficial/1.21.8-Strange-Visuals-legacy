package ru.strange.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventRender3D;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void publishWorldRenderEvent(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            CallbackInfo ci
    ) {
        MatrixStack stack = new MatrixStack();
        Matrix4f basePositionMatrix = new Matrix4f(positionMatrix);
        stack.multiplyPositionMatrix(new Matrix4f(basePositionMatrix));

        EventManager.call(new EventRender3D(stack, tickCounter.getTickProgress(true)));
    }
}