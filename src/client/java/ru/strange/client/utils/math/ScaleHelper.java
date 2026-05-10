package ru.strange.client.utils.math;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;

public class ScaleHelper {
    private MinecraftClient mc = MinecraftClient.getInstance();
    public static float size = 2;

    public static void scale_pre() {
        final ScaledResolution scaledRes = new ScaledResolution(MinecraftClient.getInstance());
        final double scale = scaledRes.getScaleFactor() / Math.pow(scaledRes.getScaleFactor(), 2);
        GL11.glPushMatrix();
        GL11.glScaled(scale * size, scale * size, scale * size);
    }

    public static void scale_post() {
        GL11.glScaled(size, size, size);
        GL11.glPopMatrix();
    }
    public static void scaleStart(float x, float y, float scale) {
        MatrixStack poseStack = new MatrixStack();
        poseStack.push();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1);
        poseStack.translate(-x, -y, 0);
    }

    public static void scaleEnd() {
        MatrixStack poseStack = new MatrixStack();
        poseStack.pop();
    }
    public static int calc(int value) {
        ScaledResolution rs = new ScaledResolution(MinecraftClient.getInstance());
        return (int) (value * rs.getScaleFactor() / size);
    }
    public static int calc(float value) {
        ScaledResolution rs = new ScaledResolution(MinecraftClient.getInstance());
        return (int) (value * rs.getScaleFactor() / size);
    }

    public static float[] calc(float mouseX, float mouseY) {
        ScaledResolution rs = new ScaledResolution(MinecraftClient.getInstance());
        mouseX = mouseX *  rs.getScaleFactor() / size;
        mouseY = mouseY *  rs.getScaleFactor() / size;
        return new float[]{mouseX, mouseY};
    }
    public static void scaleNonMatrix(float x, float y, float scale) {
        MatrixStack poseStack = new MatrixStack();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1);
        poseStack.translate(-x, -y, 0);
    }

}
