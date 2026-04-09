package com.manukq.leadphysics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.manukq.leadphysics.physics.RopePoint;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;

public final class LeadRenderHandler {

    private LeadRenderHandler() {}

    public static void render(Entity entity, Leashable leashable, float tickDelta, PoseStack poseStack, MultiBufferSource buffers, Entity leashHolder) {
        RopePoint[] points = new RopePoint[0];
    }
}
