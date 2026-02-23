package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.body.PhysicsBody;

import java.util.Objects;
import java.util.UUID;

public record PhysicsBodyLifecyclePayload(int schemaVersion,
                                          UUID worldId,
                                          String bodyId,
                                          PhysicsBody body) implements PhysicsEventPayload {
    public PhysicsBodyLifecyclePayload {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(body, "body");
    }
}
