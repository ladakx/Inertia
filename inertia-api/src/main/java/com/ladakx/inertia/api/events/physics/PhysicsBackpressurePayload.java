package com.ladakx.inertia.api.events.physics;

import java.util.Objects;
import java.util.UUID;

public record PhysicsBackpressurePayload(int schemaVersion,
                                         UUID worldId,
                                         String worldName,
                                         int pendingSnapshots,
                                         long droppedSnapshots,
                                         long overwrittenSnapshots,
                                         long backlogTicks) implements ImmutablePhysicsEventPayload {
    public PhysicsBackpressurePayload {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
    }
}
