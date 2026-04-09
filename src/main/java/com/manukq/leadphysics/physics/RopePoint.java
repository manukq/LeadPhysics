package com.manukq.leadphysics.physics;

public final class RopePoint {

    public double x;
    public double y;
    public double z;
    public double prevX;
    public double prevY;
    public double prevZ;
    public final boolean pinned;

    public RopePoint(double x, double y, double z, boolean pinned) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.pinned = pinned;
    }

    public void pin(double x, double y, double z) {
        if (!pinned) {
            return;
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
    }
}
