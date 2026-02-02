package com.ladakx.inertia.physics.world.snapshot;

import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public record PhysicsSnapshot(
        List<VisualUpdate> updates,
        Set<Long> activeChunkKeys,
        List<AbstractPhysicsBody> bodiesToDestroy
) {
    public PhysicsSnapshot {
        updates = Collections.unmodifiableList(updates);
        activeChunkKeys = Collections.unmodifiableSet(activeChunkKeys);
        bodiesToDestroy = Collections.unmodifiableList(bodiesToDestroy);
    }
}