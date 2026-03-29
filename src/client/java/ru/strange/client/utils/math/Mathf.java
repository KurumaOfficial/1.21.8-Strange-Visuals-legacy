package ru.strange.client.utils.math;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import ru.strange.client.utils.Helper;
import ru.strange.client.utils.math.animation.anim2.Interpolator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

public class Mathf implements Helper {
    public static float calculateXPosition(float x, float width) {
        return x - width / 2f;
    }
    public float wrapAngleTo180(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }
    public static final Matrix4f lastProjMat = new Matrix4f();
    public static final Matrix4f lastModMat = new Matrix4f();
    public static final Matrix4f lastWorldSpaceMatrix = new Matrix4f();
    public static Vec3d worldSpaceToScreenSpace(Vec3d pos) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        int displayHeight = mc.getWindow().getHeight();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        Vector3f target = new Vector3f();

        double deltaX = pos.x - camera.getPos().x;
        double deltaY = pos.y - camera.getPos().y;
        double deltaZ = pos.z - camera.getPos().z;

        Vector4f transformedCoordinates = new Vector4f((float) deltaX, (float) deltaY, (float) deltaZ, 1.f).mul(lastWorldSpaceMatrix);
        Matrix4f matrixProj = new Matrix4f(lastProjMat);
        Matrix4f matrixModel = new Matrix4f(lastModMat);
        matrixProj.mul(matrixModel).project(transformedCoordinates.x(), transformedCoordinates.y(), transformedCoordinates.z(), viewport, target);

        return new Vec3d(target.x / getScaleFactor(), (displayHeight - target.y) / getScaleFactor(), target.z);
    }
    public static double getScaleFactor() {
        return mc.getWindow().getScaleFactor();
    }

    public static float map(float value, float fromMin, float fromMax, float toMin, float toMax) {
        if (fromMax - fromMin == 0) return toMin; // защита от деления на 0
        return toMin + (toMax - toMin) * ((value - fromMin) / (fromMax - fromMin));
    }

    private static void validateRange(double min, double max) {
        if (max < min) {
            throw new IllegalArgumentException("max не может быть меньше min.");
        }
    }



    public static double limitDecimals(double value, int decimalPlaces) {
        return Math.round(value * Math.pow(10, decimalPlaces)) / Math.pow(10, decimalPlaces);
    }

    public float normalize(float value, float min, float max) {
        return (value - min) / (max - min);
    }


    public static Vector2f rotationToEntity(Entity target) {
        Vec3d Vec3d = target.getPos().subtract(MinecraftClient.getInstance().player.getPos());
        double magnitude = Math.hypot(Vec3d.x, Vec3d.z);
        return new Vector2f(
                (float) Math.toDegrees(Math.atan2(Vec3d.z, Vec3d.x)) - 90.0F,
                (float) (-Math.toDegrees(Math.atan2(Vec3d.y, magnitude))));
    }

    static float wrapAngleTo180_float(float p_76142_0_) {
        if ((p_76142_0_ %= 360.0f) >= 180.0f) {
            p_76142_0_ -= 360.0f;
        }
        if (p_76142_0_ < -180.0f) {
            p_76142_0_ += 360.0f;
        }
        return p_76142_0_;
    }

    public static float valWave01(float value) {
        return (value > .5 ? 1 - value : value) * 2.F;
    }

    public static double lerp(double a, double b, double f) {
        return a + f * (b - a);
    }

    public static int lerp(int a, int b, float f) {
        return a + (int) (f * (b - a));
    }


    public static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }


    public static boolean isInRegion(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= (double) x && mouseX <= (double) (x + width) && mouseY >= (double) y && mouseY <= (double) (y + height);
    }



    public double interpolate(double current, double old, double scale) {
        return old + (current - old) * scale;
    }

    public Vec3d interpolate(Vec3d end, Vec3d start, float multiple) {
        return new Vec3d(
                interpolate(end.getX(), start.getX(), multiple),
                interpolate(end.getY(), start.getY(), multiple),
                interpolate(end.getZ(), start.getZ(), multiple));
    }

    public double interporate(double p_219803_0_, double p_219803_2_, double p_219803_4_) {
        return p_219803_2_ + p_219803_0_ * (p_219803_4_ - p_219803_2_);
    }

    public static int randomInt(int min, int max) {
        return min + (int) (Math.random() * ((max - min) + 1));
    }

    public boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {

        return mouseX > x && mouseX < x + width && mouseY > y && mouseY < y + height;
    }

    public static float random1(float min, float max) {
        return (float) (Math.random() * (max - min) + min);
    }

    public static double randomWithUpdate(double min, double max, long ms, TimerUtil stopWatch) {
        double randomValue = 0;

        if (stopWatch.isReached(ms)) {
            randomValue = random1((float) min, (float) max);
            stopWatch.reset();
        }

        return randomValue;
    }

    public float fast(float end, float start, float multiple) {
        return (1 - net.minecraft.util.math.MathHelper.clamp(deltaTime() * multiple, 0, 1)) * end
                + net.minecraft.util.math.MathHelper.clamp(deltaTime() * multiple, 0, 1) * start;
    }

    public double getRandom(double min, double max) {
        if (min == max) {
            return min;
        } else if (min > max) {
            final double d = min;
            min = max;
            max = d;
        }
        return ThreadLocalRandom.current().nextDouble() * (max - min) + min;
    }

    public static float random(float min, float max) {
        return (float) (Math.random() * (max - min) + min);
    }

    public double randomValue(double min, double max) {
        validateRange(min, max);
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    public static float randomValue(float min, float max) {
        validateRange(min, max);
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    public double calcDiff(double a, double b) {
        return a - b;
    }

    public float deltaTime() {
        float debugFPS = mc.getCurrentFps();
        if (debugFPS > 0) {
            return 1.0F / debugFPS;
        } else {
            return 1.0F;
        }
    }


    public String formatTime(long millis) {
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = ((millis % 360000) % 60000) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public double round(double value, int increment) {
        double num = Math.pow(10, increment);
        return Math.round(value * num) / num;
    }
    public double round(double num, double increment) {
        double v = (double) Math.round(num / increment) * increment;
        BigDecimal bd = new BigDecimal(v);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public double round(final double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static double step(double value, double steps) {
        double roundedValue = Math.round(value / steps) * steps;
        return Math.round(roundedValue * 100.0) / 100.0;
    }

    public double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }


    public static float clamp(float min, float max, float value) {
        return Math.max(min, Math.min(max, value));
    }

    public int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    public double clamp01(double value) {
        return clamp(0.0D, 1.0D, value);
    }

    public float clamp01(float value) {
        return clamp(0.0F, 1.0F, value);
    }

    public double getDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double deltaX = calcDiff(x2, x1);
        double deltaY = calcDiff(y2, y1);
        double deltaZ = calcDiff(z2, z1);
        return net.minecraft.util.math.MathHelper.sqrt((float) (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
    }

    public double getDistance(BlockPos pos1, BlockPos pos2) {
        double deltaX = calcDiff(pos1.getX(), pos2.getX());
        double deltaY = calcDiff(pos1.getY(), pos2.getY());
        double deltaZ = calcDiff(pos1.getZ(), pos2.getZ());
        return MathHelper.sqrt((float) (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
    }

    public static float limit(float current, float inputMin, float inputMax, float outputMin, float outputMax) {
        current = Mathf.clamp(inputMin, inputMax, current);
        float distancePercentage = (current - inputMin) / (inputMax - inputMin);
        return Interpolator.lerp(outputMin, outputMax, distancePercentage);
    }
}