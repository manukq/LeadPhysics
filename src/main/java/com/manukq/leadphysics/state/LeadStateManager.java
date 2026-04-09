package com.manukq.leadphysics.state;

import com.manukq.leadphysics.physics.RopeSimulator;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class LeadStateManager {

    private static final LeadStateManager INSTANCE = new LeadStateManager();
    private static final long ANIMATION_DURATION_NS = 250_000_000L;
    private static final long STALE_MS = 5000L;

    private final Map<UUID, RopeSimulator> simulators = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAccess = new ConcurrentHashMap<>();
    private final Map<UUID, Long> animationStart = new ConcurrentHashMap<>();
    private final Map<UUID, Vec3> animationFrom = new ConcurrentHashMap<>();

    private LeadStateManager() {}

    public static LeadStateManager getInstance() {
        return INSTANCE;
    }

    public RopeSimulator getOrCreate(UUID id, Vec3 start, Vec3 end) {
        lastAccess.put(id, System.currentTimeMillis());
        return simulators.computeIfAbsent(id, key -> {
            animationStart.put(id, System.nanoTime());
            animationFrom.put(id, start);
            return new RopeSimulator(start, start);
        });
    }

    public Vec3 getEffectiveEnd(UUID id, Vec3 start, Vec3 end) {
        Long startedAt = animationStart.get(id);
        if (startedAt == null) {
            return end;
        }
        long elapsed = System.nanoTime() - startedAt;
        if (elapsed >= ANIMATION_DURATION_NS) {
            animationStart.remove(id);
            animationFrom.remove(id);
            return end;
        }
        float progress = (float) elapsed / (float) ANIMATION_DURATION_NS;
        float eased = 1.0f - (float) Math.pow(1.0 - progress, 3.0);
        Vec3 from = animationFrom.getOrDefault(id, start);
        return new Vec3(
            Mth.lerp(eased, from.x, end.x),
            Mth.lerp(eased, from.y, end.y),
            Mth.lerp(eased, from.z, end.z)
        );
    }

    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = lastAccess.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now - entry.getValue() > STALE_MS) {
                UUID id = entry.getKey();
                simulators.remove(id);
                animationStart.remove(id);
                animationFrom.remove(id);
                iterator.remove();
            }
        }
    }

    public void clear() {
        simulators.clear();
        lastAccess.clear();
        animationStart.clear();
        animationFrom.clear();
    }
}
