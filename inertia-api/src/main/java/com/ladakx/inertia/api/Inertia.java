package com.ladakx.inertia.api;

import com.ladakx.inertia.api.body.BodyFactory;

/**
 * The main access point for the Inertia API.
 * This is the contract that other plugins will use to interact with the physics world.
 */
public interface Inertia {

    /**
     * @return The factory for creating new physics bodies.
     */
    BodyFactory getBodyFactory();

    // --- Singleton Access ---

    Inertia INSTANCE = InertiaProvider.get();

    /**
     * Gets the singleton instance of the Inertia API.
     * @return The Inertia API instance.
     * @throws IllegalStateException if the API is not yet loaded.
     */
    static Inertia get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Inertia API is not available. Is the Inertia plugin enabled?");
        }
        return INSTANCE;
    }
}

