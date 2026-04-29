package com.manukq.leadphysics.render;

import com.manukq.leadphysics.config.LeadPhysicsConfig;
import com.manukq.leadphysics.physics.RopePoint;
import com.manukq.leadphysics.physics.RopeSimulator;
import com.manukq.leadphysics.state.LeadStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class LeadRenderHandler {

    private static final float  RIBBON_HALF_WIDTH = 0.042f;
    private static final float  PRISM_RADIUS      = 0.023f;
    private static final double SEGMENT_OVERLAP   = 0.0015;
    private static final int    ALPHA             = 255;
    private static final int    DARK              = 0x574634;
    private static final int    LIGHT             = 0x7E634A;

    private LeadRenderHandler() {}

    public static void render(Entity entity, Leashable leashable, float tickDelta,
                              PoseStack poseStack, MultiBufferSource buffers, Entity leashHolder) {
        LeadPhysicsConfig config = LeadPhysicsConfig.getInstance();
        if (!config.isEnabled()) return;

        Vec3 start   = entity.getPosition(tickDelta).add(leashable.getLeashOffset(tickDelta));
        Vec3 realEnd = leashHolder.getRopeHoldPosition(tickDelta);
        UUID id      = entity.getUUID();

        LeadStateManager stateManager = LeadStateManager.getInstance();
        RopeSimulator    simulator    = stateManager.getOrCreate(id, start, realEnd);
        Vec3 effectiveEnd             = stateManager.getEffectiveEnd(id, start, realEnd);
        simulator.update(entity.level(), start, effectiveEnd,
                config.getPhysicsAmplitude(), config.isBlockCollisionEnabled());

        RopePoint[] points = simulator.getPoints();
        if (points == null || points.length < 2) return;

        VertexConsumer buffer = buffers.getBuffer(RenderType.leash());
        PoseStack.Pose entry  = poseStack.last();
        Matrix4f       matrix = entry.pose();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        BlockPos startLightPos = BlockPos.containing(start);
        BlockPos endLightPos   = BlockPos.containing(realEnd);
        int startBlockLight = entity.level().getBrightness(LightLayer.BLOCK, startLightPos);
        int endBlockLight   = leashHolder.level().getBrightness(LightLayer.BLOCK, endLightPos);
        int startSkyLight   = entity.level().getBrightness(LightLayer.SKY, startLightPos);
        int endSkyLight     = leashHolder.level().getBrightness(LightLayer.SKY, endLightPos);

        int n = points.length;
        Vec3[] centers = new Vec3[n];
        for (int i = 0; i < n; i++) {
            centers[i] = new Vec3(points[i].x, points[i].y, points[i].z);
        }

        if (config.isThreeDimensional()) {
            render3D(buffer, entry, matrix, camera, centers, n,
                    startBlockLight, endBlockLight, startSkyLight, endSkyLight);
        } else {
            render2D(buffer, entry, matrix, camera, centers, n,
                    startBlockLight, endBlockLight, startSkyLight, endSkyLight);
        }
    }

    private static void render2D(VertexConsumer buffer, PoseStack.Pose entry, Matrix4f matrix,
                                 Vec3 camera, Vec3[] centers, int n,
                                 int startBlockLight, int endBlockLight,
                                 int startSkyLight, int endSkyLight) {

        Vec3[] sides = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 tangent = avgTangent(centers, i, n);
            Vec3 side = tangent.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() < 1e-6) side = tangent.cross(new Vec3(0.0, 0.0, 1.0));
            if (side.lengthSqr() < 1e-6) side = tangent.cross(new Vec3(1.0, 0.0, 0.0));
            sides[i] = side.normalize().scale(RIBBON_HALF_WIDTH);
        }

        for (int i = 0; i < n - 1; i++) {
            Vec3 a = centers[i];
            Vec3 b = centers[i + 1];
            if (a.distanceToSqr(b) < 1e-8) continue;

            float p0 = (float) i / (n - 1);
            float p1 = (float) (i + 1) / (n - 1);
            int light0 = LightTexture.pack(
                    Mth.lerpInt(p0, startBlockLight, endBlockLight),
                    Mth.lerpInt(p0, startSkyLight, endSkyLight));
            int light1 = LightTexture.pack(
                    Mth.lerpInt(p1, startBlockLight, endBlockLight),
                    Mth.lerpInt(p1, startSkyLight, endSkyLight));
            int color = (i & 1) == 0 ? DARK : LIGHT;

            Vec3 q0 = a.add(sides[i]).subtract(camera);
            Vec3 q1 = a.subtract(sides[i]).subtract(camera);
            Vec3 q2 = b.subtract(sides[i + 1]).subtract(camera);
            Vec3 q3 = b.add(sides[i + 1]).subtract(camera);

            Vec3 normal = q1.subtract(q0).cross(q3.subtract(q0));
            if (normal.lengthSqr() < 1e-8) continue;
            normal = normal.normalize();

            vertex(buffer, entry, matrix, q0, light0, normal, color);
            vertex(buffer, entry, matrix, q1, light0, normal, color);
            vertex(buffer, entry, matrix, q2, light1, normal, color);
            vertex(buffer, entry, matrix, q3, light1, normal, color);
        }
    }

    private static Vec3 avgTangent(Vec3[] centers, int i, int n) {
        Vec3 t;
        if (i == 0) t = centers[1].subtract(centers[0]);
        else if (i == n - 1) t = centers[n - 1].subtract(centers[n - 2]);
        else t = centers[i + 1].subtract(centers[i - 1]);
        return t.lengthSqr() < 1e-8 ? new Vec3(1.0, 0.0, 0.0) : t.normalize();
    }

    private static void render3D(VertexConsumer buffer, PoseStack.Pose entry, Matrix4f matrix,
                                 Vec3 camera, Vec3[] centers, int n,
                                 int startBlockLight, int endBlockLight,
                                 int startSkyLight, int endSkyLight) {

        Vec3[] tangents = new Vec3[n];
        for (int k = 0; k < n; k++) {
            tangents[k] = avgTangent(centers, k, n);
        }

        Vec3[] frameA = new Vec3[n];
        Vec3[] frameB = new Vec3[n];
        Vec3 ref = Math.abs(tangents[0].y) > 0.9 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 a0 = tangents[0].cross(ref);
        if (a0.lengthSqr() < 1e-8) a0 = tangents[0].cross(new Vec3(1, 0, 0));
        a0 = a0.normalize();
        frameA[0] = a0;
        frameB[0] = tangents[0].cross(a0).normalize();

        for (int k = 1; k < n; k++) {
            Vec3 t = tangents[k];
            Vec3 fa = frameA[k - 1].subtract(t.scale(frameA[k - 1].dot(t)));
            if (fa.lengthSqr() < 1e-8) fa = tangents[k - 1].cross(t);
            if (fa.lengthSqr() < 1e-8) fa = t.cross(frameB[k - 1]);
            if (fa.lengthSqr() < 1e-8) {
                Vec3 f2 = Math.abs(t.y) > 0.9 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
                fa = t.cross(f2);
            }
            fa = fa.normalize();
            if (fa.dot(frameA[k - 1]) < 0) fa = fa.scale(-1);
            frameA[k] = fa;
            Vec3 fb = t.cross(fa);
            frameB[k] = fb.lengthSqr() < 1e-8 ? frameB[k - 1] : fb.normalize();
        }

        for (int i = 0; i < n - 1; i++) {
            Vec3 a = centers[i];
            Vec3 b = centers[i + 1];
            if (a.distanceToSqr(b) < 1e-8) continue;

            float p0 = (float) i / (n - 1);
            float p1 = (float) (i + 1) / (n - 1);
            int light0 = LightTexture.pack(
                    Mth.lerpInt(p0, startBlockLight, endBlockLight),
                    Mth.lerpInt(p0, startSkyLight, endSkyLight));
            int light1 = LightTexture.pack(
                    Mth.lerpInt(p1, startBlockLight, endBlockLight),
                    Mth.lerpInt(p1, startSkyLight, endSkyLight));
            int color = (i & 1) == 0 ? DARK : LIGHT;

            renderPrismSegment(buffer, entry, matrix, camera, a, b,
                    tangents[i], tangents[i + 1],
                    frameA[i].scale(PRISM_RADIUS), frameA[i + 1].scale(PRISM_RADIUS),
                    frameB[i].scale(PRISM_RADIUS), frameB[i + 1].scale(PRISM_RADIUS),
                    light0, light1, color);
        }
    }

    private static void renderPrismSegment(VertexConsumer buffer, PoseStack.Pose entry, Matrix4f matrix,
                                           Vec3 camera, Vec3 a, Vec3 b,
                                           Vec3 tangentA, Vec3 tangentB,
                                           Vec3 axisA0, Vec3 axisA1,
                                           Vec3 axisB0, Vec3 axisB1,
                                           int light0, int light1, int baseColor) {
        Vec3 start = a.subtract(tangentA.scale(SEGMENT_OVERLAP));
        Vec3 end = b.add(tangentB.scale(SEGMENT_OVERLAP));

        Vec3 axisA = axisA0.add(axisA1);
        if (axisA.lengthSqr() < 1e-8) axisA = axisA0.lengthSqr() > 1e-8 ? axisA0 : axisA1;
        axisA = axisA.normalize().scale(PRISM_RADIUS);

        Vec3 axisB = axisB0.add(axisB1);
        if (axisB.lengthSqr() < 1e-8) axisB = axisB0.lengthSqr() > 1e-8 ? axisB0 : axisB1;
        axisB = axisB.normalize().scale(PRISM_RADIUS);

        Vec3 a00 = start.add(axisA).add(axisB).subtract(camera);
        Vec3 a01 = start.add(axisA).subtract(axisB).subtract(camera);
        Vec3 a10 = start.subtract(axisA).subtract(axisB).subtract(camera);
        Vec3 a11 = start.subtract(axisA).add(axisB).subtract(camera);
        Vec3 b00 = end.add(axisA).add(axisB).subtract(camera);
        Vec3 b01 = end.add(axisA).subtract(axisB).subtract(camera);
        Vec3 b10 = end.subtract(axisA).subtract(axisB).subtract(camera);
        Vec3 b11 = end.subtract(axisA).add(axisB).subtract(camera);

        Vec3 nA = axisA.normalize();
        Vec3 nB = axisB.normalize();
        quad(buffer, entry, matrix, a00, a01, b01, b00, nA, light0, light1, baseColor);
        quad(buffer, entry, matrix, a01, a10, b10, b01, nB.scale(-1), light0, light1, baseColor);
        quad(buffer, entry, matrix, a10, a11, b11, b10, nA.scale(-1), light0, light1, baseColor);
        quad(buffer, entry, matrix, a11, a00, b00, b11, nB, light0, light1, baseColor);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose entry, Matrix4f matrix,
                             Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4,
                             Vec3 normal, int light0, int light1, int color) {
        vertex(buffer, entry, matrix, p1, light0, normal, color);
        vertex(buffer, entry, matrix, p2, light0, normal, color);
        vertex(buffer, entry, matrix, p3, light1, normal, color);
        vertex(buffer, entry, matrix, p4, light1, normal, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose entry, Matrix4f matrix,
                               Vec3 pos, int light, Vec3 normal, int color) {
        buffer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, ALPHA)
                .setLight(light)
                .setNormal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}