package com.manukq.leadphysics.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RopeSimulator {

    public static final int SEGMENTS = 32;
    private static final int CONSTRAINT_ITERS = 12;
    private static final double GRAVITY = 0.006;
    private static final double DAMPING = 0.985;
    private static final double OSCILLATION_AMPLITUDE = 0.002;
    private static final double OSCILLATION_SPEED = 0.08;

    private final RopePoint[] points;
    private double restSegmentLength;
    private long tickCount;

    public RopeSimulator(Vec3 start, Vec3 end) {
        points = new RopePoint[SEGMENTS + 1];
        double distance = start.distanceTo(end);
        restSegmentLength = Math.max(distance / SEGMENTS, 0.04);
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            double sag = Math.sin(Math.PI * t) * distance * 0.08;
            points[i] = new RopePoint(
                Mth.lerp(t, start.x, end.x),
                Mth.lerp(t, start.y, end.y) - sag,
                Mth.lerp(t, start.z, end.z),
                i == 0 || i == SEGMENTS
            );
        }
    }

    public void update(Vec3 start, Vec3 end) {
        tickCount++;
        double distance = start.distanceTo(end);
        restSegmentLength = Math.max(distance / SEGMENTS, 0.04);

        points[0].pin(start.x, start.y, start.z);
        points[SEGMENTS].pin(end.x, end.y, end.z);

        for (int i = 1; i < SEGMENTS; i++) {
            RopePoint point = points[i];
            double velocityX = (point.x - point.prevX) * DAMPING;
            double velocityY = (point.y - point.prevY) * DAMPING;
            double velocityZ = (point.z - point.prevZ) * DAMPING;

            double phase = tickCount * OSCILLATION_SPEED + i * Math.PI / SEGMENTS;
            double oscillationX = Math.sin(phase) * OSCILLATION_AMPLITUDE;
            double oscillationZ = Math.cos(phase * 0.7) * OSCILLATION_AMPLITUDE;

            point.prevX = point.x;
            point.prevY = point.y;
            point.prevZ = point.z;
            point.x += velocityX + oscillationX;
            point.y += velocityY - GRAVITY;
            point.z += velocityZ + oscillationZ;
        }

        for (int iteration = 0; iteration < CONSTRAINT_ITERS; iteration++) {
            for (int i = 0; i < SEGMENTS; i++) {
                RopePoint a = points[i];
                RopePoint b = points[i + 1];
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double dz = b.z - a.z;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared < 1.0e-12) {
                    continue;
                }
                double currentDistance = Math.sqrt(distanceSquared);
                double factor = (currentDistance - restSegmentLength) / currentDistance * 0.5;
                double correctionX = dx * factor;
                double correctionY = dy * factor;
                double correctionZ = dz * factor;
                if (!a.pinned) {
                    a.x += correctionX;
                    a.y += correctionY;
                    a.z += correctionZ;
                }
                if (!b.pinned) {
                    b.x -= correctionX;
                    b.y -= correctionY;
                    b.z -= correctionZ;
                }
            }
        }
    }

    public RopePoint[] getPoints() {
        return points;
    }
}
