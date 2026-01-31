package com.ladakx.inertia.physics.world.snapshot;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Immutable container representing the state of the physics world at a specific tick.
 * Passed atomically from the Physics Thread to the Bukkit Main Thread.
 */
public record PhysicsSnapshot(
        List<VisualUpdate> updates,
        Set<Long> activeChunkKeys
) {
    public PhysicsSnapshot {
        updates = Collections.unmodifiableList(updates);
        activeChunkKeys = Collections.unmodifiableSet(activeChunkKeys);
    }
}