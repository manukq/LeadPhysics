package com.manukq.leadphysics.mixin;

import com.manukq.leadphysics.config.LeadPhysicsConfig;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public final class EntityRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void suppressVanillaLeash(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (LeadPhysicsConfig.getInstance().isEnabled() && entity instanceof Leashable leashable && leashable.getLeashHolder() != null) {
            state.leashStates = null;
        }
    }
}
