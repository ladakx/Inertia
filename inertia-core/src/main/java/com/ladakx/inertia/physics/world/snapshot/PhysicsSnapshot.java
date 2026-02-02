package com.ladakx.inertia.physics.world.snapshot;

import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;

import java.util.List;
import java.util.Set;

public record PhysicsSnapshot(
        List<VisualState> updates,
        Set<Long> activeChunkKeys,
        List<AbstractPhysicsBody> bodiesToDestroy
) {
    public void release(SnapshotPool pool) {
        if (updates != null) {
            for (VisualState state : updates) {
                pool.returnState(state);
            }
            pool.returnList(updates);
        }
    }
}