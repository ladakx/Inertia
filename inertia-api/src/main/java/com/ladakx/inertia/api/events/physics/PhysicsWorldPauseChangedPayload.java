package com.ladakx.inertia.api.events.physics;

import java.util.Objects;
import java.util.UUID;

public record PhysicsWorldPauseChangedPayload(int schemaVersion,
                                              UUID worldId,
                                              String worldName,
                                              boolean paused) implements ImmutablePhysicsEventPayload {
    public PhysicsWorldPauseChangedPayload {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
    }
}
