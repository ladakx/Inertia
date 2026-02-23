package com.ladakx.inertia.api.events.physics;

import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.UUID;

public record PhysicsCollisionPayload(int schemaVersion,
                                      UUID worldId,
                                      String bodyAId,
                                      String bodyBId,
                                      Vector contactPoint) implements PhysicsEventPayload {
    public PhysicsCollisionPayload {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(bodyAId, "bodyAId");
        Objects.requireNonNull(bodyBId, "bodyBId");
        Objects.requireNonNull(contactPoint, "contactPoint");
        contactPoint = contactPoint.clone();
    }
}
