package com.ladakx.inertia.api.events.physics;

import java.util.Objects;
import java.util.UUID;

public record PhysicsWorldOverloadPayload(int schemaVersion,
                                          UUID worldId,
                                          String worldName,
                                          long tickNumber,
                                          double tickDurationMs,
                                          int configuredTps,
                                          long overloadedTicks) implements ImmutablePhysicsEventPayload {
    public PhysicsWorldOverloadPayload {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
    }
}
