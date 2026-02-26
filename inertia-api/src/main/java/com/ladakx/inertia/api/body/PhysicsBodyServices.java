package com.ladakx.inertia.api.body;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

/**
 * Well-known service keys for physics-body integrations.
 */
public final class PhysicsBodyServices {

    public static final @NotNull ServiceKey<PhysicsBodiesService> BODIES =
            new ServiceKey<>("inertia.physics.bodies", PhysicsBodiesService.class);

    private PhysicsBodyServices() {
    }
}

