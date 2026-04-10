package com.manukq.leadphysics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class LeadPhysicsConfig {

    public enum RenderMode {
        TWO_D,
        THREE_D
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("leadphysics.json");
    private static final LeadPhysicsConfig INSTANCE = new LeadPhysicsConfig();

    private boolean enabled = true;
    private RenderMode renderMode = RenderMode.THREE_D;
    private double physicsAmplitude = 1.0;
    private boolean blockCollision = true;

    public static LeadPhysicsConfig getInstance() {
        return INSTANCE;
    }

    public void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) {
                save();
                return;
            }
            enabled = data.enabled == null ? true : data.enabled;
            renderMode = data.renderMode == null ? RenderMode.THREE_D : data.renderMode;
            physicsAmplitude = clampAmplitude(data.physicsAmplitude == null ? 1.0 : data.physicsAmplitude);
            blockCollision = data.blockCollision == null ? true : data.blockCollision;
        } catch (IOException ignored) {
        }
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(new Data(enabled, renderMode, clampAmplitude(physicsAmplitude), blockCollision), writer);
            }
        } catch (IOException ignored) {
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RenderMode getRenderMode() {
        return renderMode;
    }

    public void setRenderMode(RenderMode renderMode) {
        this.renderMode = renderMode;
    }

    public boolean isThreeDimensional() {
        return renderMode == RenderMode.THREE_D;
    }

    public double getPhysicsAmplitude() {
        return physicsAmplitude;
    }

    public void setPhysicsAmplitude(double physicsAmplitude) {
        this.physicsAmplitude = clampAmplitude(physicsAmplitude);
    }

    public boolean isBlockCollisionEnabled() {
        return blockCollision;
    }

    public void setBlockCollision(boolean blockCollision) {
        this.blockCollision = blockCollision;
    }

    private double clampAmplitude(double value) {
        return Math.max(0.0, Math.min(3.0, value));
    }

    private record Data(Boolean enabled, RenderMode renderMode, Double physicsAmplitude, Boolean blockCollision) {}
}
