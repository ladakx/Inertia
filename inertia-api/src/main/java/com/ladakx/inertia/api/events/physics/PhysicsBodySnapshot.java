package com.ladakx.inertia.api.events.physics;

import java.util.Objects;
import java.util.UUID;

public record PhysicsBodySnapshot(int schemaVersion,
                                  UUID worldId,
                                  String bodyId) implements ImmutablePhysicsEventPayload {
    public PhysicsBodySnapshot {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(bodyId, "bodyId");
    }
}
