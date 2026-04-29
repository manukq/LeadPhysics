package com.manukq.leadphysics;

import com.manukq.leadphysics.config.LeadPhysicsConfig;
import com.manukq.leadphysics.state.LeadStateManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class LeadPhysicsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LeadPhysicsConfig.getInstance().load();
        ClientTickEvents.END_LEVEL_TICK.register(world -> LeadStateManager.getInstance().tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LeadStateManager.getInstance().clear());
    }
}
