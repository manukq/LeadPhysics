package com.manukq.leadphysics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RopeSimulator {

    public static final int SEGMENTS = 32;
    private static final int CONSTRAINT_ITERS = 14;
    private static final double BASE_GRAVITY = 0.006;
    private static final double DAMPING = 0.985;
    private static final double BASE_OSCILLATION_AMPLITUDE = 0.0018;
    private static final double OSCILLATION_SPEED = 0.08;
    private static final double POINT_RADIUS = 0.06;

    private final RopePoint[] points;
    private double restSegmentLength;
    private long tickCount;

    public RopeSimulator(Vec3 start, Vec3 end) {
        points = new RopePoint[SEGMENTS + 1];
        seed(start, end, 1.0);
    }

    public void update(Level level, Vec3 start, Vec3 end, double amplitude) {
        tickCount++;
        double clampedAmplitude = Math.max(0.0, Math.min(3.0, amplitude));
        double distance = start.distanceTo(end);
        restSegmentLength = Math.max(distance / SEGMENTS, 0.04);

        if (distance > restSegmentLength * SEGMENTS * 2.5) {
            seed(start, end, clampedAmplitude);
        }

        points[0].pin(start.x, start.y, start.z);
        points[SEGMENTS].pin(end.x, end.y, end.z);

        double gravity = BASE_GRAVITY * clampedAmplitude;
        double oscillationAmplitude = BASE_OSCILLATION_AMPLITUDE * clampedAmplitude;

        for (int i = 1; i < SEGMENTS; i++) {
            RopePoint point = points[i];
            double velocityX = (point.x - point.prevX) * DAMPING;
            double velocityY = (point.y - point.prevY) * DAMPING;
            double velocityZ = (point.z - point.prevZ) * DAMPING;
            double phase = tickCount * OSCILLATION_SPEED + i * Math.PI / SEGMENTS;
            double oscillationX = Math.sin(phase) * oscillationAmplitude;
            double oscillationZ = Math.cos(phase * 0.7) * oscillationAmplitude;

            point.prevX = point.x;
            point.prevY = point.y;
            point.prevZ = point.z;
            point.x += velocityX + oscillationX;
            point.y += velocityY - gravity;
            point.z += velocityZ + oscillationZ;
        }

        for (int iteration = 0; iteration < CONSTRAINT_ITERS; iteration++) {
            constrainDistance();
            resolveBlockCollisions(level);
            points[0].pin(start.x, start.y, start.z);
            points[SEGMENTS].pin(end.x, end.y, end.z);
        }
    }

    public RopePoint[] getPoints() {
        return points;
    }

    private void seed(Vec3 start, Vec3 end, double amplitude) {
        double distance = start.distanceTo(end);
        restSegmentLength = Math.max(distance / SEGMENTS, 0.04);
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            double sag = Math.sin(Math.PI * t) * distance * 0.08 * amplitude;
            points[i] = new RopePoint(
                Mth.lerp(t, start.x, end.x),
                Mth.lerp(t, start.y, end.y) - sag,
                Mth.lerp(t, start.z, end.z),
                i == 0 || i == SEGMENTS
            );
        }
    }

    private void constrainDistance() {
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

    private void resolveBlockCollisions(Level level) {
        for (int i = 1; i < SEGMENTS; i++) {
            RopePoint point = points[i];
            int minX = Mth.floor(point.x - POINT_RADIUS);
            int maxX = Mth.floor(point.x + POINT_RADIUS);
            int minY = Mth.floor(point.y - POINT_RADIUS);
            int maxY = Mth.floor(point.y + POINT_RADIUS);
            int minZ = Mth.floor(point.z - POINT_RADIUS);
            int maxZ = Mth.floor(point.z + POINT_RADIUS);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos blockPos = new BlockPos(x, y, z);
                        VoxelShape shape = level.getBlockState(blockPos).getCollisionShape(level, blockPos);
                        if (shape.isEmpty()) {
                            continue;
                        }
                        for (AABB box : shape.toAabbs()) {
                            AABB worldBox = box.move(blockPos);
                            if (!intersects(point, worldBox)) {
                                continue;
                            }
                            resolvePointAgainstBox(point, worldBox);
                        }
                    }
                }
            }
        }
    }

    private boolean intersects(RopePoint point, AABB box) {
        double closestX = Mth.clamp(point.x, box.minX, box.maxX);
        double closestY = Mth.clamp(point.y, box.minY, box.maxY);
        double closestZ = Mth.clamp(point.z, box.minZ, box.maxZ);
        double dx = point.x - closestX;
        double dy = point.y - closestY;
        double dz = point.z - closestZ;
        return dx * dx + dy * dy + dz * dz < POINT_RADIUS * POINT_RADIUS;
    }

    private void resolvePointAgainstBox(RopePoint point, AABB box) {
        double distanceLeft = Math.abs(point.x - box.minX);
        double distanceRight = Math.abs(box.maxX - point.x);
        double distanceDown = Math.abs(point.y - box.minY);
        double distanceUp = Math.abs(box.maxY - point.y);
        double distanceBack = Math.abs(point.z - box.minZ);
        double distanceFront = Math.abs(box.maxZ - point.z);

        double min = distanceUp;
        Direction direction = Direction.UP;
        if (distanceDown < min) {
            min = distanceDown;
            direction = Direction.DOWN;
        }
        if (distanceLeft < min) {
            min = distanceLeft;
            direction = Direction.LEFT;
        }
        if (distanceRight < min) {
            min = distanceRight;
            direction = Direction.RIGHT;
        }
        if (distanceBack < min) {
            min = distanceBack;
            direction = Direction.BACK;
        }
        if (distanceFront < min) {
            direction = Direction.FRONT;
        }

        switch (direction) {
            case UP -> point.setPos(point.x, box.maxY + POINT_RADIUS, point.z);
            case DOWN -> point.setPos(point.x, box.minY - POINT_RADIUS, point.z);
            case LEFT -> point.setPos(box.minX - POINT_RADIUS, point.y, point.z);
            case RIGHT -> point.setPos(box.maxX + POINT_RADIUS, point.y, point.z);
            case BACK -> point.setPos(point.x, point.y, box.minZ - POINT_RADIUS);
            case FRONT -> point.setPos(point.x, point.y, box.maxZ + POINT_RADIUS);
        }

        point.prevX = point.x;
        point.prevY = point.y;
        point.prevZ = point.z;
    }

    private enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        BACK,
        FRONT
    }
}
