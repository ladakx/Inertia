package com.ladakx.inertia.api.events.physics;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Async physics-thread event emitted when a collision contact is generated.
 * Payload is read-only and versioned via schemaVersion.
 * Bukkit API calls are limited to thread-safe operations only.
 */
public class PhysicsCollisionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsBody bodyA;
    private final PhysicsBody bodyB;
    private final PhysicsCollisionPayload payload;

    public PhysicsCollisionEvent(@NotNull PhysicsBody bodyA, @NotNull PhysicsBody bodyB, @NotNull Vector contactPoint) {
        super(true);
        this.bodyA = Objects.requireNonNull(bodyA, "bodyA");
        this.bodyB = Objects.requireNonNull(bodyB, "bodyB");
        this.payload = new PhysicsCollisionPayload(
                PhysicsEventPayload.SCHEMA_VERSION_V1,
                Objects.requireNonNull(bodyA.getLocation().getWorld(), "bodyA.location.world").getUID(),
                bodyA.getBodyId(),
                bodyB.getBodyId(),
                Objects.requireNonNull(contactPoint, "contactPoint")
        );
    }

    public @NotNull PhysicsBody getBodyA() {
        return bodyA;
    }

    public @NotNull PhysicsBody getBodyB() {
        return bodyB;
    }

    public @NotNull Vector getContactPoint() {
        return payload.contactPoint().clone();
    }

    public @NotNull PhysicsCollisionPayload getPayload() {
        return payload;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
