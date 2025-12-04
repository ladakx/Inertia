package com.ladakx.inertia.jolt.snapshot;

import java.util.Collections;
import java.util.List;

/**
 * Immutable container representing the state of the physics world at a specific tick.
 * Passed atomically from the Physics Thread to the Bukkit Main Thread.
 */
public record PhysicsSnapshot(List<VisualUpdate> updates) {
    public PhysicsSnapshot {
        updates = Collections.unmodifiableList(updates);
    }
}