package ru.strange.client.utils.math.animation;

public final class Translate {
    private float x;
    private float y;

    public Translate(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void interpolate(float targetX, float targetY, float smoothing) {
        this.x = animate(targetX, this.x, smoothing);
        this.y = animate(targetY, this.y, smoothing);
    }

    public void animate(float newX, float newY) {
        this.x = animate(this.x, newX, 1.0f);
        this.y = animate(this.y, newY, 1.0f);
    }

    public float animate(float target, float current, float speed) {
        if (speed < 0.0f) speed = 0.0f;
        if (speed > 1.0f) speed = 1.0f;

        float dif = target - current;
        float factor = Math.abs(dif) * speed;

        if (factor < 0.1f) {
            return target;
        }

        return current + (dif > 0 ? factor : -factor);
    }

    public float getX() {
        return this.x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return this.y;
    }

    public void setY(float y) {
        this.y = y;
    }
}