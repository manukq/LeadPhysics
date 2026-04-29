package com.manukq.leadphysics;

import com.manukq.leadphysics.config.LeadPhysicsConfig;
import com.manukq.leadphysics.render.LeadRenderHandler;
import com.manukq.leadphysics.state.LeadStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;

public final class LeadPhysicsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LeadPhysicsConfig.getInstance().load();
        ClientTickEvents.END_WORLD_TICK.register(world -> LeadStateManager.getInstance().tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LeadStateManager.getInstance().clear());
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!LeadPhysicsConfig.getInstance().isEnabled()) return;
            MultiBufferSource buffers = context.consumers();
            if (buffers == null) return;
            PoseStack poseStack = context.matrixStack();
            if (poseStack == null) return;
            float tickDelta = context.tickCounter().getGameTimeDeltaPartialTick(true);
            for (Entity entity : context.world().entitiesForRendering()) {
                if (!(entity instanceof Leashable leashable)) continue;
                Entity holder = leashable.getLeashHolder();
                if (holder == null) continue;
                LeadRenderHandler.render(entity, leashable, tickDelta, poseStack, buffers, holder);
            }
        });
    }
}
