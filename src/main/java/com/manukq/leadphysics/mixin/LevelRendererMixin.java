package com.manukq.leadphysics.mixin;

import com.manukq.leadphysics.config.LeadPhysicsConfig;
import com.manukq.leadphysics.render.LeadRenderHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public final class LevelRendererMixin {

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void renderLeadPhysics(PoseStack poseStack, LevelRenderState renderState, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (!LeadPhysicsConfig.getInstance().isEnabled()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float tickDelta = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof Leashable leashable)) continue;
            Entity holder = leashable.getLeashHolder();
            if (holder == null) continue;
            LeadRenderHandler.render(entity, leashable, tickDelta, poseStack, buffers, holder);
        }
    }
}
