package com.ladakx.inertia.api.events;

import java.util.Objects;
import java.util.UUID;

public record PhysicsCollisionPayload(int schemaVersion,
                                      UUID worldId,
                                      String bodyAId,
                                      String bodyBId,
                                      double contactPointX,
                                      double contactPointY,
                                      double contactPointZ) implements ImmutablePhysicsEventPayload {
    public PhysicsCollisionPayload {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(bodyAId, "bodyAId");
        Objects.requireNonNull(bodyBId, "bodyBId");
    }
}
