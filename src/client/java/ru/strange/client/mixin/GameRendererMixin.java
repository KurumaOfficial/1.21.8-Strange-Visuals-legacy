package ru.strange.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import me.x150.renderer.event.RenderEvents;
import me.x150.renderer.util.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.Strange;
import ru.strange.client.module.impl.other.AspectRation;
import ru.strange.client.module.impl.other.NoRender;
import ru.strange.client.module.impl.other.SmoothCamera;
import ru.strange.client.module.impl.world.BlockOutline;
import ru.strange.client.renderengine.renderers.util.FrameTracker;
import ru.strange.client.utils.other.FreeLookHandler;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private static float strange$lastValidAspect = 16.0F / 9.0F;

    @Shadow
    public abstract float getFarPlaneDistance();

    @WrapOperation(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
            )
    )
    void renderer_postWorldRender(WorldRenderer instance, ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, Operation<Void> original) {
        // Disable vanilla block outline if custom BlockOutline module is enabled
        boolean disableVanillaOutline = Strange.get != null && Strange.get.manager != null 
                && Strange.get.manager.getModule(BlockOutline.class) != null
                && Strange.get.manager.getModule(BlockOutline.class).enable;
        
        Matrix4f worldPositionMatrix = new Matrix4f(positionMatrix);
        SmoothCamera smoothCamera = SmoothCamera.getInstance();
        if (smoothCamera != null && smoothCamera.isVisualActive()) {
            MinecraftClient client = MinecraftClient.getInstance();
            Perspective perspective = client.options.getPerspective();
            boolean thirdPerson = perspective == Perspective.THIRD_PERSON_BACK || perspective == Perspective.THIRD_PERSON_FRONT;
            boolean inverseView = perspective == Perspective.THIRD_PERSON_FRONT;

            smoothCamera.updateVisualEffect(
                    camera.getYaw(),
                    camera.getPitch(),
                    tickCounter.getTickProgress(true),
                    thirdPerson,
                    inverseView,
                    FreeLookHandler.isActive()
            );

            MatrixStack visualStack = new MatrixStack();
            visualStack.multiplyPositionMatrix(worldPositionMatrix);
            smoothCamera.applyWorldTransform(visualStack);
            worldPositionMatrix = new Matrix4f(visualStack.peek().getPositionMatrix());
        } else if (smoothCamera != null) {
            smoothCamera.resetVisualSmoothing();
        }

        original.call(instance, allocator, tickCounter, disableVanillaOutline ? false : renderBlockOutline, camera, worldPositionMatrix, projectionMatrix, fogBuffer, fogColor, renderSky);

        Profiler profiler = Profilers.get();
        profiler.swap("rendererLibWorld");

        MatrixStack matrix = new MatrixStack();
        matrix.multiplyPositionMatrix(worldPositionMatrix);

        RenderUtils.lastProjMat.set(projectionMatrix);
        RenderUtils.lastModMat.set(RenderSystem.getModelViewMatrix());
        RenderUtils.lastWorldSpaceMatrix.set(matrix.peek().getPositionMatrix());
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, RenderUtils.lastViewport);

        RenderEvents.AFTER_WORLD.invoker().rendered(matrix);

        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("HEAD"), cancellable = true)
    public void getBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        if (Strange.get == null || Strange.get.manager == null) {
            return;
        }

        var module = Strange.get.manager.getModule(AspectRation.class);
        if (module == null || !module.enable) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        float aspect = strange$resolveSafeAspect(client);
        Matrix4f matrix4f = new Matrix4f();
        cir.setReturnValue(matrix4f.perspective(
                (fovDegrees * (float) (Math.PI / 180.0)),
                aspect,
                0.05F,
                this.getFarPlaneDistance()
        ));
    }

    @Unique
    private static float strange$resolveSafeAspect(MinecraftClient client) {
        int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());
        float baseAspect = framebufferWidth / (float) framebufferHeight;
        float adjustedAspect = baseAspect + AspectRation.getAspectRation();

        if (client.getWindow().isMinimized()
                || client.getWindow().hasZeroWidthOrHeight()
                || !Float.isFinite(adjustedAspect)
                || adjustedAspect <= 0.01F) {
            adjustedAspect = strange$lastValidAspect;
        }

        if (!Float.isFinite(adjustedAspect) || adjustedAspect <= 0.01F) {
            adjustedAspect = baseAspect;
        }

        if (!Float.isFinite(adjustedAspect) || adjustedAspect <= 0.01F) {
            adjustedAspect = 16.0F / 9.0F;
        }

        strange$lastValidAspect = adjustedAspect;
        return adjustedAspect;
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    public void strange$removeHurtTilt(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (NoRender.enabled("Убрать тряску")) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        FrameTracker.onFrameStart();
    }
}
