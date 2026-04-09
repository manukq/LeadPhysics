package com.manukq.leadphysics.config;

public final class LeadPhysicsConfig {

    private static final LeadPhysicsConfig INSTANCE = new LeadPhysicsConfig();

    private boolean enabled = true;
    private boolean threeDimensional = true;
    private double physicsAmplitude = 1.0;

    public static LeadPhysicsConfig getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isThreeDimensional() {
        return threeDimensional;
    }

    public double getPhysicsAmplitude() {
        return physicsAmplitude;
    }
}
