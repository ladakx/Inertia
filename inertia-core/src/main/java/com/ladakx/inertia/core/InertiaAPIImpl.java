package com.ladakx.inertia.core;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.body.BodyFactory;
import com.ladakx.inertia.api.world.PhysicsWorld;
import com.ladakx.inertia.core.body.BodyFactoryImpl;
import com.ladakx.inertia.core.physics.PhysicsManager;

public class InertiaAPIImpl extends InertiaAPI {

    private final BodyFactory bodyFactory;

    public InertiaAPIImpl(PhysicsManager physicsManager) {
        super(physicsManager);
        this.bodyFactory = new BodyFactoryImpl(physicsManager);
    }

    @Override
    public BodyFactory getBodyFactory() {
        return this.bodyFactory;
    }
}
