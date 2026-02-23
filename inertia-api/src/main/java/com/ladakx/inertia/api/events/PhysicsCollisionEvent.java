package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = false)
public class PhysicsCollisionEvent extends com.ladakx.inertia.api.events.physics.PhysicsCollisionEvent {
    public PhysicsCollisionEvent(@NotNull PhysicsBody bodyA, @NotNull PhysicsBody bodyB, @NotNull Vector contactPoint) {
        super(bodyA, bodyB, contactPoint);
    }
}
