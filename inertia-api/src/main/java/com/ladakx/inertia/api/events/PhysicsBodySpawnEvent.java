package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = false)
public class PhysicsBodySpawnEvent extends com.ladakx.inertia.api.events.physics.PhysicsBodyPostSpawnEvent {
    public PhysicsBodySpawnEvent(@NotNull PhysicsBody body) {
        super(body);
    }
}
