package ru.strange.client.event.impl;

import net.minecraft.client.util.math.Vector2f;
import net.minecraft.util.math.Box;
import ru.strange.client.event.Event;

public class EventMotion extends Event {
    private float yaw, pitch;
    private double posX, posY, posZ;
    private boolean onGround;
    private Box aabbFrom;
    Runnable postMotion;

    public EventMotion(float yaw, float pitch, double posX, double posY, double posZ, boolean onGround, Box aabbFrom, Runnable postMotion){
        this.yaw = yaw;
        this.pitch = pitch;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.onGround = onGround;
        this.aabbFrom = aabbFrom;
        this.postMotion = postMotion;
    }
    public void setRotate(Vector2f vector2f) {
        setYaw(vector2f.getX());
        setPitch(vector2f.getY());
    }

    public Box getAabbFrom() {
        return aabbFrom;
    }

    public void setAabbFrom(Box aabbFrom) {
        this.aabbFrom = aabbFrom;
    }

    public Runnable getPostMotion() {
        return postMotion;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public double getPosX() {
        return posX;
    }

    public double getPosY() {
        return posY;
    }

    public double getPosZ() {
        return posZ;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setPostMotion(Runnable postMotion) {
        this.postMotion = postMotion;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setPosX(double posX) {
        this.posX = posX;
    }

    public void setPosY(double posY) {
        this.posY = posY;
    }

    public void setPosZ(double posZ) {
        this.posZ = posZ;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }
}
