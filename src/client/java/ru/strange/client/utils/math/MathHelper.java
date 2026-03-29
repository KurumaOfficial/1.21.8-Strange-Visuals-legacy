package ru.strange.client.utils.math;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import org.joml.Vector3d;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Random;

public class MathHelper {
    public static MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();
    public static int scale = 2;
    public static double interporate(double x, double y, double z) {
        return y + x * (z - y);
    }
    public static double getNormalDouble(double d, int numberAfterZopyataya) {
        return (new BigDecimal(d)).setScale(numberAfterZopyataya, RoundingMode.HALF_EVEN).doubleValue();
    }
    public static float normalize(float value, float min, float max) {
        return (value - min) / (max - min);
    }
    public static double random(double min, double max) {
        return Math.random() * (max - min) + min;
    }

    public static float random(float min, float max) {
        return (float) (Math.random() * (max - min) + min);
    }
    public static boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height){
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
    public static double getNormalDouble(double d) {
        return (new BigDecimal(d)).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }
    public static double interpolateNew(double old, double current, double scale) {
        return old + (current - old) * scale;
    }
    public static float wrapAngleTo180_float(float p_76142_0_) {
        if ((p_76142_0_ %= 360.0f) >= 180.0f) {
            p_76142_0_ -= 360.0f;
        }
        if (p_76142_0_ < -180.0f) {
            p_76142_0_ += 360.0f;
        }
        return p_76142_0_;
    }
    public static float randomizeFloat(float min, float max) {
        return (float) (min + Math.random() * (max - min));
    }
    public static double sq(double a) {
        return a * a;
    }

    public static double cathet(double h, double a) {
        return Math.sqrt(sq(h) - sq(a));
    }

    public static float calculateHeight(float width) {
        return (width * 9) / 16;
    }

    public static float calculateWidth(float height) {
        return (height * 16) / 9;
    }

    public static int calc(int value) {
        Window mainWindow = MinecraftClient.getInstance().getWindow();
        return (int)((double)value * mainWindow.getScaleFactor() / (double)scale);
    }
    private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] ASINE_TAB = new double[257];
    private static final double[] COS_TAB = new double[257];

    public static double atan2(double p_181159_0_, double p_181159_2_) {
        double d0 = p_181159_2_ * p_181159_2_ + p_181159_0_ * p_181159_0_;

        if (Double.isNaN(d0)) {
            return Double.NaN;
        } else {
            boolean flag = p_181159_0_ < 0.0D;

            if (flag) {
                p_181159_0_ = -p_181159_0_;
            }

            boolean flag1 = p_181159_2_ < 0.0D;

            if (flag1) {
                p_181159_2_ = -p_181159_2_;
            }

            boolean flag2 = p_181159_0_ > p_181159_2_;

            if (flag2) {
                double d1 = p_181159_2_;
                p_181159_2_ = p_181159_0_;
                p_181159_0_ = d1;
            }

            double d9 = fastInvSqrt(d0);
            p_181159_2_ = p_181159_2_ * d9;
            p_181159_0_ = p_181159_0_ * d9;
            double d2 = FRAC_BIAS + p_181159_0_;
            int i = (int) Double.doubleToRawLongBits(d2);
            double d3 = ASINE_TAB[i];
            double d4 = COS_TAB[i];
            double d5 = d2 - FRAC_BIAS;
            double d6 = p_181159_0_ * d4 - p_181159_2_ * d5;
            double d7 = (6.0D + d6 * d6) * d6 * 0.16666666666666666D;
            double d8 = d3 + d7;

            if (flag2) {
                d8 = (Math.PI / 2D) - d8;
            }

            if (flag1) {
                d8 = Math.PI - d8;
            }

            if (flag) {
                d8 = -d8;
            }

            return d8;
        }
    }
    public static double fastInvSqrt(double number) {
        double d0 = 0.5D * number;
        long i = Double.doubleToRawLongBits(number);
        i = 6910469410427058090L - (i >> 1);
        number = Double.longBitsToDouble(i);
        return number * (1.5D - d0 * number * number);
    }

    public static double getDifferenceOf(double num1, double num2) {
        return Math.abs(num2 - num1) > Math.abs(num1 - num2) ? Math.abs(num1 - num2) : Math.abs(num2 - num1);
    }

    public static double easeInOutQuad(double x, int step) {
        return x < 0.5D ? 2.0D * x * x : 1.0D - Math.pow(-2.0D * x + 2.0D, (double)step) / 2.0D;
    }

    public static float roundToDecimal(float value, int decimalPlaces) {
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("Decimal places must be non-negative");
        } else {
            double multiplier = Math.pow(10.0D, (double)decimalPlaces);
            return (float)((double)Math.round((double)value * multiplier) / multiplier);
        }
    }

    public static double getDistanceXZ(Vector3d vector1, Vector3d vector2) {
        double distanceX = vector2.x - vector1.x;
        double distanceZ = vector2.z - vector1.z;
        return Math.sqrt(distanceX * distanceX + distanceZ * distanceZ);
    }

    public float calc(float value) {
        Window mainWindow = MinecraftClient.getInstance().getWindow();
        return value * (float)((int)((double)value * mainWindow.getScaleFactor() / (double)scale));
    }

    public static int ceil(double value)
    {
        int i = (int)value;
        return value > (double)i ? i + 1 : i;
    }

    public double calc(double value) {
        Window mainWindow = MinecraftClient.getInstance().getWindow();
        return value * (double)((int)(value * mainWindow.getScaleFactor() / (double)scale));
    }

    public static float getSensitivity(float rot) {
        return getDeltaMouse(rot) * getGCDValue();
    }

    public static float getGCDValue() {
        return (float) (getGCD() * 0.15);
    }

    public static float getGCD() {
        float f1;
        return (f1 = (float) (mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2)) * f1 * f1 * 8;
    }

    public static float getDeltaMouse(float delta) {
        return Math.round(delta / getGCDValue());
    }

    public static double getBps(Entity e) {
        double prevZ = e.getZ() - e.lastZ;
        double prevX = e.getX() - e.lastX;
        double prevY = e.getY() - e.lastY;
        double lastDist = Math.sqrt(prevX * prevX + prevZ * prevZ + prevY * prevY);
        double currSpeed = lastDist * 15.3571428571D;
        return currSpeed;
    }

    public static float wrapDegrees(float value) {
        if ((value = (float)((double)value % 360.0D)) >= 180.0F) {
            value -= 360.0F;
        }

        if (value < -180.0F) {
            value += 360.0F;
        }

        return value;
    }

    public static float invertScaleValue(float value, float minInput, float maxInput, float minOutput, float maxOutput) {
        if (maxInput - minInput == 0) {
            throw new IllegalArgumentException("\u0414\u0438\u0430\u043F\u0430\u0437\u043E\u043D \u0432\u0445\u043E\u0434\u043D\u044B\u0445 \u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0439 \u043D\u0435 \u043C\u043E\u0436\u0435\u0442 \u0431\u044B\u0442\u044C \u0440\u0430\u0432\u0435\u043D \u043D\u0443\u043B\u044E.");
        }

        float scaledValue = (maxInput - value) / (maxInput - minInput) * (maxOutput - minOutput) + minOutput;

        return Math.max(minOutput, Math.min(maxOutput, scaledValue));
    }

    public static float scaleValue(float value, float minInput, float maxInput, float minOutput, float maxOutput) {
        if (maxInput - minInput == 0) {
            throw new IllegalArgumentException("\u0414\u0438\u0430\u043F\u0430\u0437\u043E\u043D \u0432\u0445\u043E\u0434\u043D\u044B\u0445 \u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0439 \u043D\u0435 \u043C\u043E\u0436\u0435\u0442 \u0431\u044B\u0442\u044C \u0440\u0430\u0432\u0435\u043D \u043D\u0443\u043B\u044E.");
        }

        float scaledValue = (value - minInput) / (maxInput - minInput) * (maxOutput - minOutput) + minOutput;

        return Math.max(minOutput, Math.min(maxOutput, scaledValue));
    }

    public static float calcPercentage(float value, float min, float max) {
        if (!(value < min) && !(value > max)) {
            float range = max - min;
            float percentage = (value - min) / range * 100.0F;
            return percentage;
        } else {
            return 0;
        }
    }

    public static float calcPercentage0_100(float value, float min, float max) {
        if (!(value < min) && !(value > max)) {
            float range = max - min;
            float percentage = (value - min) / range * 101;
            return percentage;
        } else {
            return 0;
        }
    }
    public static float calcPercentage0_190(float value, float min, float max) {
        if (!(value < min) && !(value > max)) {
            float range = max - min;
            float percentage = (value - min) / range * 191;
            return percentage;
        } else {
            return 0;
        }
    }
    public static float calculateValue(float percentage, float min, float max) {
        if (!(percentage < 0.0F) && !(percentage > 100.0F)) {
            float range = max - min;
            return percentage / 100.0F * range + min;
        } else {
            return 0;
        }
    }

    public static double getRandomInRange(double max, double min) {
        return min + (max - min) * random.nextDouble();
    }

    public static BigDecimal round(float f, int times) {
        BigDecimal bd = new BigDecimal(Float.toString(f));
        bd = bd.setScale(times, 4);
        return bd;
    }

    public static int getRandomInRange(int max, int min) {
        return (int)((double)min + (double)(max - min) * random.nextDouble());
    }

    public static boolean isEven(int number) {
        return number % 2 == 0;
    }

    public static double roundToPlace(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        } else {
            BigDecimal bd = new BigDecimal(value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }
    }

    public static double preciseRound(double value, double precision) {
        double scale = Math.pow(10.0D, precision);
        return (double)Math.round(value * scale) / scale;
    }

    public static double randomNumber(double max, double min) {
        return Math.random() * (max - min) + min;
    }

    public static int randomize(int max, int min) {
        return -min + (int)(Math.random() * (double)(max - -min + 1));
    }

    public static float randomFloat(float f2, float f3) {
        return f2 != f3 && !(f3 - f2 <= 0.0F) ? (float)((double)f2 + (double)(f3 - f2) * Math.random()) : f2;
    }

    public static int randomInt(int min, int max) {
        return random.nextInt(max - min) + min;
    }

    public static double getIncremental(double val, double inc) {
        double one = 1.0D / inc;
        return (double)Math.round(val * one) / one;
    }

    public static boolean isInteger(Double variable) {
        return variable == Math.floor(variable) && !Double.isInfinite(variable);
    }

    public static float[] constrainAngle(float[] vector) {
        vector[0] %= 360.0F;

        for(vector[1] %= 360.0F; vector[0] <= -180.0F; vector[0] += 360.0F) {
        }

        while(vector[1] <= -180.0F) {
            vector[1] += 360.0F;
        }

        while(vector[0] > 180.0F) {
            vector[0] -= 360.0F;
        }

        while(vector[1] > 180.0F) {
            vector[1] -= 360.0F;
        }

        return vector;
    }

    public static double randomize(double min, double max) {
        Random random = new Random();
        double range = max - min;
        double scaled = random.nextDouble() * range;
        if (scaled > max) {
            scaled = max;
        }

        double shifted;
        if ((shifted = scaled + min) > max) {
            shifted = max;
        }

        return shifted;
    }

    public static float percentLerp(float initialValue, float finalValue, float speed, float acceleration) {
        float speedloc = calculateValue(acceleration, 0.0F, speed);
        return lerp(initialValue, finalValue, speedloc);
    }

    public static float harmonic(float startValue, float endValue, float speed) {
        float to = startValue + speed / 2.0F;
        if (to > endValue) {
            to = endValue;
        }

        return to;
    }

    public static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }

    public static float harp(float val, float current, float f, float speed) {
        float emi = (current - val) * (speed / 2.0F) > 0.0F ? Math.max(speed, Math.min(current - val, (current - val) * (speed / 2.0F))) : Math.max(current - val, Math.min(-(speed / 2.0F), (current - val) * (speed / 2.0F)));
        return f + emi;
    }

    public static float harp(float val, float current, float speed) {
        float emi = (current - val) * (speed / 2.0F) > 0.0F ? Math.max(speed, Math.min(current - val, (current - val) * (speed / 2.0F))) : Math.max(current - val, Math.min(-(speed / 2.0F), (current - val) * (speed / 2.0F)));
        return val + emi;
    }

    public static double roundToDecimalPlace(double value, double inc) {
        double halfOfInc = inc / 2.0D;
        double floored = Math.floor(value / inc) * inc;
        return value >= floored + halfOfInc ? (new BigDecimal(Math.ceil(value / inc) * inc, MathContext.DECIMAL64)).stripTrailingZeros().doubleValue() : (new BigDecimal(floored, MathContext.DECIMAL64)).stripTrailingZeros().doubleValue();
    }

    public static float lerpRandom(float a, float b, float f, float random) {
        Random randomz = new Random();
        float randomFactor = randomz.nextFloat() * random;
        return a + f * randomFactor * (b - a);
    }
    public static int clampI(int val, int min, int max) {
        if (val <= min) {
            val = min;
        }

        if (val >= max) {
            val = max;
        }

        return val;
    }
    public static float clampF(float val, float min, float max) {
        if (val <= min) {
            val = min;
        }

        if (val >= max) {
            val = max;
        }

        return val;
    }


    //millis to HH:MM:SS
    public static String format(long millis) {
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = ((millis % 360000) % 60000) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    public static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        } else {
            return value;
        }
    }
    // interpolate
    public static double interpolate (double current, double old, double scale) {
        return old + (current - old) * scale;
    }
    public static float interpolate (float current, float old, double scale) {
        return (float) interpolate((double) current, (double) old, scale);
    }
    public static int interpolate (int current, int old, double scale) {
        return (int) interpolate((double) current, (double) old, scale);
    }
    public static Vector3d interpolate(Vector3d end, Vector3d start, float multiple) {
        return new Vector3d(
                interpolate(end.x, start.x, multiple),
                interpolate(end.y, start.y, multiple),
                interpolate(end.z, start.z, multiple));
    }

    public static double round(double num, double increment) {
        double v = (double)Math.round(num / increment) * increment;
        BigDecimal bd = new BigDecimal(v);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
    public static int getRandomNumberBetween(int min, int max) {
        return (int) (Math.random() * (max - min + 1) + min);
    }

    public static double getRandomNumberBetween(double min, double max) {
        return Math.random() * (max - min) + min;
    }
    public static Vector3d fast(Vector3d end, Vector3d start, float multiple) {
        return new Vector3d(
                fast((float) end.x, (float) start.x, multiple),
                fast((float) end.y, (float) start.y, multiple),
                fast((float) end.z, (float) start.z, multiple));
    }
    public static float fast(float end, float start, float multiple) {
        return (1 - clamp((float) (deltaTime() * multiple), 0, 1)) * end
                + clamp((float) (deltaTime() * multiple), 0, 1) * start;
    }
    public static double deltaTime() {
        return MinecraftClient.getInstance().getCurrentFps() > 0 ? (1.0000 / MinecraftClient.getInstance().getCurrentFps()) : 1;
    }
    public static int ceiling_double_int(double p_76143_0_) {
        int var2 = (int) p_76143_0_;
        return p_76143_0_ > var2 ? var2 + 1 : var2;
    }

    public static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }



    public static float map(float value, float istart, float istop, float ostart, float ostop) {
        return ostart + (ostop - ostart) * (value - istart) / (istop - istart);
    }

    public static float intRandom(float max, float min) {
        return (float) ((Math.random() * (max - min)) + min);
    }

    public static float interpolate(float current, float old, float scale) {
        return current + (old - current) * clamp(scale, 0.0F, 1.0F);
    }



    public static double lerp(double current, double old, double scale) {
        return current + (old - current) * clamp((float) scale, 0.0F, 1.0F);
    }

    public static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        }
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static int getCenter(int width, int rectWidth) {
        return width / 2 - rectWidth / 2;
    }

    public static float getRandomInRange(float min, float max) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;
    }




    public static float interpolateFloat(float oldValue, float newValue, double interpolationValue){
        return interpolate(oldValue, newValue, (float) interpolationValue);
    }


    public static float calculateGaussianValue(float x, float sigma) {
        double PI = 3.141592653;
        double output = 1.0 / Math.sqrt(2.0 * PI * (sigma * sigma));
        return (float) (output * Math.exp(-(x * x) / (2.0 * (sigma * sigma))));
    }

    public static double roundToHalf(double d) {
        return Math.round(d * 2) / 2.0;
    }

    public static float getRandomFloat(float max, float min) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;

    }
    public static int getMiddle(int old, int newValue) {
        return (old + newValue) / 2;
    }
    public static int intRandom(int max, int min)
    {
        return (int)(Math.random() * (double)(max - min)) + min;
    }


    public static float checkAngle(float one, float two, float three) {
        float f = wrapDegrees(one - two);
        if (f < -three) {
            f = -three;
        }
        if (f >= three) {
            f = three;
        }
        return one - f;
    }

    public static float clamp01(float x) {
        return (float) clamp3(0, 1, x);
    }
    public static double clamp3(double min, double max, double n) {
        return Math.max(min, Math.min(max, n));
    }
}
