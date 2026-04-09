package com.manukq.leadphysics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.manukq.leadphysics.render.LeadRenderHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public final class EntityRenderDispatcherMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("TAIL")
    )
    private <E extends Entity> void renderCustomLead(E entity, double x, double y, double z, float tickDelta, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof Leashable leashable)) {
            return;
        }
        Entity leashHolder = leashable.getLeashHolder();
        if (leashHolder == null) {
            return;
        }
        LeadRenderHandler.render(entity, leashable, tickDelta, poseStack, buffers, leashHolder);
    }
}
