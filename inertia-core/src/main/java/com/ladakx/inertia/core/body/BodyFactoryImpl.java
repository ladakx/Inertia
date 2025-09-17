package com.ladakx.inertia.core.body;

import com.ladakx.inertia.api.body.BodyBuilder;
import com.ladakx.inertia.api.body.BodyFactory;
import com.ladakx.inertia.core.physics.PhysicsManager;

public class BodyFactoryImpl implements BodyFactory {

    private final PhysicsManager physicsManager;

    public BodyFactoryImpl(PhysicsManager physicsManager) {
        this.physicsManager = physicsManager;
    }

    @Override
    public BodyBuilder newBuilder() {
        return new BodyBuilderImpl(physicsManager);
    }
}
