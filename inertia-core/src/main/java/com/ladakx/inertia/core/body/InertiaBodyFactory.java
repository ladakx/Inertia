package com.ladakx.inertia.core.body;

import com.ladakx.inertia.api.body.BodyBuilder;
import com.ladakx.inertia.api.body.BodyFactory;
import com.ladakx.inertia.core.engine.PhysicsEngine;

/**
 * Internal implementation of the BodyFactory.
 */
public class InertiaBodyFactory implements BodyFactory {

    private final PhysicsEngine engine;

    public InertiaBodyFactory(PhysicsEngine engine) {
        this.engine = engine;
    }

    @Override
    public BodyBuilder newBuilder() {
        return new InertiaBodyBuilder(this.engine);
    }
}