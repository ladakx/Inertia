package com.ladakx.inertia.api.events;

import java.util.Objects;
import java.util.UUID;

public record PhysicsWorldTickPayload(int schemaVersion,
                                      UUID worldId,
                                      String worldName,
                                      boolean paused,
                                      int configuredTps) implements ImmutablePhysicsEventPayload {
    public PhysicsWorldTickPayload {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
    }
}
