package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = false)
public class PhysicsBodyDestroyEvent extends com.ladakx.inertia.api.events.physics.PhysicsBodyPostDestroyEvent {
    public PhysicsBodyDestroyEvent(@NotNull PhysicsBody body) {
        super(body);
    }
}
