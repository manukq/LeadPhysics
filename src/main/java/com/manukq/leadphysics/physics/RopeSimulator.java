package com.manukq.leadphysics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RopeSimulator {

    public static final int SEGMENTS = 32;
    private static final int CONSTRAINT_ITERS = 14;
    private static final double BASE_GRAVITY = 0.006;
    private static final double DAMPING = 0.972;
    private static final double SLEEP_VELOCITY_SQR = 1.0e-6;
    private static final double SLEEP_ANCHOR_DELTA = 0.0025;
    private static final int SLEEP_TICKS = 12;
    private static final double PLAYER_INFLUENCE_RADIUS = 0.35;
    private static final double PLAYER_STEP_HORIZONTAL_RADIUS = 0.2;
    private static final double PLAYER_STEP_HEIGHT_BELOW = 0.1;
    private static final double PLAYER_STEP_HEIGHT_ABOVE = 0.16;
    private static final double PLAYER_STEP_DOWNWARD = 0.018;
    private static final double PLAYER_STEP_DRAG = 0.12;
    private static final double POINT_RADIUS = 0.06;
    private static final double COLLISION_BIAS = 0.0025;

    private final RopePoint[] points;
    private double restSegmentLength;
    private long tickCount;
    private Vec3 lastStart = Vec3.ZERO;
    private Vec3 lastEnd = Vec3.ZERO;
    private int stillTicks;
    private boolean sleeping;

    public RopeSimulator(Vec3 start, Vec3 end) {
        points = new RopePoint[SEGMENTS + 1];
        seed(start, end, 1.0);
    }

    public void update(Level level, Vec3 start, Vec3 end, double amplitude, boolean blockCollisionEnabled) {
        double clampedAmplitude = Math.max(0.0, Math.min(3.0, amplitude));
        double distance = start.distanceTo(end);
        restSegmentLength = Math.max(distance / SEGMENTS, 0.04);

        if (distance > restSegmentLength * SEGMENTS * 2.5) {
            seed(start, end, clampedAmplitude);
        }

        points[0].pin(start.x, start.y, start.z);
        points[SEGMENTS].pin(end.x, end.y, end.z);

        double anchorMotion = start.distanceTo(lastStart) + end.distanceTo(lastEnd);
        if (anchorMotion > SLEEP_ANCHOR_DELTA) {
            sleeping = false;
            stillTicks = 0;
        }

        boolean disturbedByPlayer = applyPlayerDisturbance(level, clampedAmplitude);
        if (disturbedByPlayer) {
            sleeping = false;
            stillTicks = 0;
        }

        if (sleeping) {
            lastStart = start;
            lastEnd = end;
            return;
        }

        double gravity = BASE_GRAVITY * clampedAmplitude;
        double maxVelocitySqr = 0.0;
        boolean[] collided = blockCollisionEnabled ? new boolean[SEGMENTS + 1] : null;

        for (int i = 1; i < SEGMENTS; i++) {
            RopePoint point = points[i];
            double velocityX = (point.x - point.prevX) * DAMPING;
            double velocityY = (point.y - point.prevY) * DAMPING;
            double velocityZ = (point.z - point.prevZ) * DAMPING;
            maxVelocitySqr = Math.max(maxVelocitySqr, velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);

            point.prevX = point.x;
            point.prevY = point.y;
            point.prevZ = point.z;
            point.x += velocityX;
            point.y += velocityY - gravity;
            point.z += velocityZ;
        }

        for (int iteration = 0; iteration < CONSTRAINT_ITERS; iteration++) {
            constrainDistance();
            if (blockCollisionEnabled) {
                resolveBlockCollisions(level, collided);
            }
            points[0].pin(start.x, start.y, start.z);
            points[SEGMENTS].pin(end.x, end.y, end.z);
        }

        if (collided != null) {
            dampCollidedPoints(collided);
        }

        if (anchorMotion <= SLEEP_ANCHOR_DELTA && maxVelocitySqr <= SLEEP_VELOCITY_SQR) {
            stillTicks++;
            if (stillTicks >= SLEEP_TICKS) {
                sleeping = true;
                settle();
            }
        } else {
            stillTicks = 0;
        }

        lastStart = start;
        lastEnd = end;
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
        lastStart = start;
        lastEnd = end;
        stillTicks = 0;
        sleeping = false;
    }

    private boolean applyPlayerDisturbance(Level level, double amplitude) {
        AABB ropeBounds = createBounds().inflate(PLAYER_INFLUENCE_RADIUS);
        boolean disturbed = false;
        for (Player player : level.getEntitiesOfClass(Player.class, ropeBounds, Player::isAlive)) {
            if (!player.onGround()) {
                continue;
            }
            AABB bounds = player.getBoundingBox();
            double footY = bounds.minY;
            Vec3 movement = player.getDeltaMovement();
            Vec3 horizontalMovement = new Vec3(movement.x, 0.0, movement.z);
            for (int i = 3; i < SEGMENTS - 2; i++) {
                RopePoint point = points[i];
                if (point.y < footY - PLAYER_STEP_HEIGHT_BELOW || point.y > footY + PLAYER_STEP_HEIGHT_ABOVE) {
                    continue;
                }
                double horizontalDistanceSqr = horizontalDistanceSqr(point, bounds);
                if (horizontalDistanceSqr > PLAYER_STEP_HORIZONTAL_RADIUS * PLAYER_STEP_HORIZONTAL_RADIUS) {
                    continue;
                }
                double pressure = 1.0 - Math.sqrt(horizontalDistanceSqr) / PLAYER_STEP_HORIZONTAL_RADIUS;
                point.y -= PLAYER_STEP_DOWNWARD * (0.6 + amplitude * 0.2) * pressure;
                if (horizontalMovement.lengthSqr() > 1.0e-5) {
                    point.x += horizontalMovement.x * PLAYER_STEP_DRAG * pressure;
                    point.z += horizontalMovement.z * PLAYER_STEP_DRAG * pressure;
                }
                disturbed = true;
            }
        }
        return disturbed;
    }

    private double horizontalDistanceSqr(RopePoint point, AABB bounds) {
        double closestX = Mth.clamp(point.x, bounds.minX, bounds.maxX);
        double closestZ = Mth.clamp(point.z, bounds.minZ, bounds.maxZ);
        double dx = point.x - closestX;
        double dz = point.z - closestZ;
        return dx * dx + dz * dz;
    }

    private AABB createBounds() {
        AABB bounds = new AABB(points[0].x, points[0].y, points[0].z, points[0].x, points[0].y, points[0].z);
        for (int i = 1; i <= SEGMENTS; i++) {
            bounds = bounds.minmax(new AABB(points[i].x, points[i].y, points[i].z, points[i].x, points[i].y, points[i].z));
        }
        return bounds;
    }

    private void settle() {
        for (int i = 1; i < SEGMENTS; i++) {
            RopePoint point = points[i];
            point.prevX = point.x;
            point.prevY = point.y;
            point.prevZ = point.z;
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

    private void resolveBlockCollisions(Level level, boolean[] collided) {
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
                            collided[i] = true;
                        }
                    }
                }
            }
        }
    }

    private void dampCollidedPoints(boolean[] collided) {
        for (int i = 1; i < SEGMENTS; i++) {
            if (!collided[i]) {
                continue;
            }
            RopePoint point = points[i];
            point.prevX = Mth.lerp(0.75, point.prevX, point.x);
            point.prevY = Mth.lerp(0.75, point.prevY, point.y);
            point.prevZ = Mth.lerp(0.75, point.prevZ, point.z);
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
            case UP -> point.setPos(point.x, box.maxY + POINT_RADIUS + COLLISION_BIAS, point.z);
            case DOWN -> point.setPos(point.x, box.minY - POINT_RADIUS - COLLISION_BIAS, point.z);
            case LEFT -> point.setPos(box.minX - POINT_RADIUS - COLLISION_BIAS, point.y, point.z);
            case RIGHT -> point.setPos(box.maxX + POINT_RADIUS + COLLISION_BIAS, point.y, point.z);
            case BACK -> point.setPos(point.x, point.y, box.minZ - POINT_RADIUS - COLLISION_BIAS);
            case FRONT -> point.setPos(point.x, point.y, box.maxZ + POINT_RADIUS + COLLISION_BIAS);
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
