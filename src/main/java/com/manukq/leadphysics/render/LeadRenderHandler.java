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

    private static final float RIBBON_HALF_WIDTH = 0.042f;
    private static final float PRISM_HALF_WIDTH = 0.042f;
    private static final float PRISM_HALF_HEIGHT = 0.022f;
    private static final int ALPHA = 255;
    private static final int DARK = 0x574634;
    private static final int LIGHT = 0x7E634A;

    private LeadRenderHandler() {}

    public static void render(Entity entity, Leashable leashable, float tickDelta, PoseStack poseStack, MultiBufferSource buffers, Entity leashHolder) {
        LeadPhysicsConfig config = LeadPhysicsConfig.getInstance();
        if (!config.isEnabled()) {
            return;
        }

        Vec3 start = entity.getPosition(tickDelta).add(leashable.getLeashOffset(tickDelta));
        Vec3 realEnd = leashHolder.getRopeHoldPosition(tickDelta);
        UUID id = entity.getUUID();
        LeadStateManager stateManager = LeadStateManager.getInstance();
        RopeSimulator simulator = stateManager.getOrCreate(id, start, realEnd);
        Vec3 effectiveEnd = stateManager.getEffectiveEnd(id, start, realEnd);
        simulator.update(entity.level(), start, effectiveEnd, config.getPhysicsAmplitude(), config.isBlockCollisionEnabled());

        RopePoint[] points = simulator.getPoints();
        if (points.length < 2) {
            return;
        }

        VertexConsumer buffer = buffers.getBuffer(RenderType.leash());
        PoseStack.Pose entry = poseStack.last();
        Matrix4f matrix = entry.pose();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BlockPos startLightPos = BlockPos.containing(start);
        BlockPos endLightPos = BlockPos.containing(realEnd);
        int startBlockLight = entity.level().getBrightness(LightLayer.BLOCK, startLightPos);
        int endBlockLight = leashHolder.level().getBrightness(LightLayer.BLOCK, endLightPos);
        int startSkyLight = entity.level().getBrightness(LightLayer.SKY, startLightPos);
        int endSkyLight = leashHolder.level().getBrightness(LightLayer.SKY, endLightPos);
        Vec3[] centers = new Vec3[points.length];
        Vec3[] ribbonSides = config.isThreeDimensional() ? null : new Vec3[points.length];
        Vec3[] prismWidths = config.isThreeDimensional() ? new Vec3[points.length] : null;
        Vec3[] prismHeights = config.isThreeDimensional() ? new Vec3[points.length] : null;

        for (int i = 0; i < points.length; i++) {
            centers[i] = point(points[i]);
        }

        if (!config.isThreeDimensional()) {
            for (int i = 0; i < centers.length; i++) {
                Vec3 tangent;
                if (i == 0) {
                    tangent = centers[1].subtract(centers[0]);
                } else if (i == centers.length - 1) {
                    tangent = centers[i].subtract(centers[i - 1]);
                } else {
                    tangent = centers[i + 1].subtract(centers[i - 1]);
                }
                if (tangent.lengthSqr() < 1.0e-8) {
                    tangent = new Vec3(1.0, 0.0, 0.0);
                } else {
                    tangent = tangent.normalize();
                }
                Vec3 side = tangent.cross(camera.subtract(centers[i]));
                if (side.lengthSqr() < 1.0e-8) {
                    side = tangent.cross(new Vec3(0.0, 1.0, 0.0));
                }
                if (side.lengthSqr() < 1.0e-8) {
                    side = new Vec3(1.0, 0.0, 0.0);
                }
                ribbonSides[i] = side.normalize().scale(RIBBON_HALF_WIDTH);
            }
        } else {
            for (int i = 0; i < centers.length; i++) {
                Vec3 tangent;
                if (i == 0) {
                    tangent = centers[1].subtract(centers[0]);
                } else if (i == centers.length - 1) {
                    tangent = centers[i].subtract(centers[i - 1]);
                } else {
                    tangent = centers[i + 1].subtract(centers[i - 1]);
                }
                if (tangent.lengthSqr() < 1.0e-8) {
                    tangent = new Vec3(1.0, 0.0, 0.0);
                } else {
                    tangent = tangent.normalize();
                }
                Vec3 toCamera = camera.subtract(centers[i]);
                Vec3 widthAxis = tangent.cross(toCamera);
                if (widthAxis.lengthSqr() < 1.0e-8) {
                    widthAxis = tangent.cross(new Vec3(0.0, 1.0, 0.0));
                }
                if (widthAxis.lengthSqr() < 1.0e-8) {
                    widthAxis = new Vec3(1.0, 0.0, 0.0);
                } else {
                    widthAxis = widthAxis.normalize();
                }
                Vec3 heightAxis = widthAxis.cross(tangent);
                if (heightAxis.lengthSqr() < 1.0e-8) {
                    heightAxis = new Vec3(0.0, 1.0, 0.0);
                } else {
                    heightAxis = heightAxis.normalize();
                }
                prismWidths[i] = widthAxis.scale(PRISM_HALF_WIDTH);
                prismHeights[i] = heightAxis.scale(PRISM_HALF_HEIGHT);
            }
        }

        for (int i = 0; i < points.length - 1; i++) {
            float progress0 = (float) i / (points.length - 1);
            float progress1 = (float) (i + 1) / (points.length - 1);
            int light0 = LightTexture.pack(
                Mth.lerpInt(progress0, startBlockLight, endBlockLight),
                Mth.lerpInt(progress0, startSkyLight, endSkyLight)
            );
            int light1 = LightTexture.pack(
                Mth.lerpInt(progress1, startBlockLight, endBlockLight),
                Mth.lerpInt(progress1, startSkyLight, endSkyLight)
            );

            Vec3 a = centers[i];
            Vec3 b = centers[i + 1];
            if (config.isThreeDimensional()) {
                renderPrismSegment(buffer, entry, matrix, camera, a, b, prismWidths[i], prismWidths[i + 1], prismHeights[i], prismHeights[i + 1], light0, light1);
            } else {
                renderRibbonSegment(buffer, entry, matrix, camera, a, b, ribbonSides[i], ribbonSides[i + 1], light0, light1);
            }
        }
    }

    private static Vec3 point(RopePoint point) {
        return new Vec3(point.x, point.y, point.z);
    }

    private static void renderRibbonSegment(VertexConsumer buffer, PoseStack.Pose entry, Matrix4f matrix, Vec3 camera, Vec3 a, Vec3 b, Vec3 sideA, Vec3 sideB, int light0, int light1) {
        Vec3 tangent = b.subtract(a);
        if (tangent.lengthSqr() < 1.0e-8) {
            return;
        }
        tangent = tangent.normalize();
        Vec3 joinedSide = sideA.add(sideB);
        if (joinedSide.lengthSqr() < 1.0e-8) {
            joinedSide = sideA.lengthSqr() > 1.0e-8 ? sideA : sideB;
        }
        Vec3 normal = joinedSide.cross(tangent);
        if (normal.lengthSqr() < 1.0e-8) {
            normal = tangent.cross(camera.subtract(a));
        }
        if (normal.lengthSqr() < 1.0e-8) {
            normal = new Vec3(0.0, 1.0, 0.0);
        } else {
            normal = normal.normalize();
        }

        Vec3 a0 = a.add(sideA).subtract(camera);
        Vec3 a1 = a.subtract(sideA).subtract(camera);
        Vec3 b0 = b.add(sideB).subtract(camera);
        Vec3 b1 = b.subtract(sideB).subtract(camera);

        quad(buffer, entry, matrix, a0, a1, b1, b0, normal, light0, light1, DARK);
        quad(buffer, entry, matrix, b0, b1, a1, a0, normal.scale(-1.0), light1, light0, LIGHT);
    }

    private static void renderPrismSegment(VertexConsumer buffer, PoseStack.Pose entry, Matrix4f matrix, Vec3 camera, Vec3 a, Vec3 b, Vec3 widthA, Vec3 widthB, Vec3 heightA, Vec3 heightB, int light0, int light1) {
        Vec3 topLeftA = a.subtract(widthA).add(heightA).subtract(camera);
        Vec3 topRightA = a.add(widthA).add(heightA).subtract(camera);
        Vec3 bottomLeftA = a.subtract(widthA).subtract(heightA).subtract(camera);
        Vec3 bottomRightA = a.add(widthA).subtract(heightA).subtract(camera);
        Vec3 topLeftB = b.subtract(widthB).add(heightB).subtract(camera);
        Vec3 topRightB = b.add(widthB).add(heightB).subtract(camera);
        Vec3 bottomLeftB = b.subtract(widthB).subtract(heightB).subtract(camera);
        Vec3 bottomRightB = b.add(widthB).subtract(heightB).subtract(camera);

        Vec3 topNormal = heightA.add(heightB).normalize();
        Vec3 bottomNormal = topNormal.scale(-1.0);
        Vec3 leftNormal = widthA.add(widthB).normalize().scale(-1.0);
        Vec3 rightNormal = leftNormal.scale(-1.0);

        quad(buffer, entry, matrix, topLeftA, topRightA, topRightB, topLeftB, topNormal, light0, light1, DARK);
        quad(buffer, entry, matrix, bottomRightA, bottomLeftA, bottomLeftB, bottomRightB, bottomNormal, light0, light1, LIGHT);
        quad(buffer, entry, matrix, bottomLeftA, topLeftA, topLeftB, bottomLeftB, leftNormal, light0, light1, DARK);
        quad(buffer, entry, matrix, topRightA, bottomRightA, bottomRightB, topRightB, rightNormal, light0, light1, LIGHT);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose entry, Matrix4f matrix, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, Vec3 normal, int light0, int light1, int color) {
        vertex(buffer, entry, matrix, p1, light0, normal, color);
        vertex(buffer, entry, matrix, p2, light0, normal, color);
        vertex(buffer, entry, matrix, p3, light1, normal, color);
        vertex(buffer, entry, matrix, p4, light1, normal, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose entry, Matrix4f matrix, Vec3 position, int light, Vec3 normal, int color) {
        buffer.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
            .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, ALPHA)
            .setLight(light)
            .setNormal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }

}
