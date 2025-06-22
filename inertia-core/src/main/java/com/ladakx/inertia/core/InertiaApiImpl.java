package com.ladakx.inertia.core;

import com.ladakx.inertia.api.Inertia;
import com.ladakx.inertia.api.body.BodyFactory;
import com.ladakx.inertia.core.body.InertiaBodyFactory;

/**
 * The internal implementation of the Inertia API.
 * This class connects the public interfaces with the core logic.
 */
public final class InertiaApiImpl implements Inertia {

    private final InertiaCore core;
    private final BodyFactory bodyFactory;

    public InertiaApiImpl(InertiaCore core) {
        this.core = core;
        // We will create the actual factory later, for now it's a placeholder
        this.bodyFactory = new InertiaBodyFactory(core.getPhysicsEngine());
    }

    @Override
    public BodyFactory getBodyFactory() {
        // We will return the real factory instance here
        return bodyFactory;
    }
}