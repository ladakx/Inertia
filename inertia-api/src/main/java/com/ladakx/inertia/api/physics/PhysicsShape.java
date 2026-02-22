package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for physics collision shapes used by the public API.
 * <p>
 * Implementations are simple immutable data holders; the engine converts them to internal/native shapes.
 * This design keeps the API extensible without exposing engine-specific classes.
 */
public interface PhysicsShape {
    @NotNull Kind kind();

    enum Kind {
        BOX,
        SPHERE,
        CAPSULE,
        CYLINDER,
        TAPERED_CAPSULE,
        TAPERED_CYLINDER,
        CONVEX_HULL,
        COMPOUND,
        CUSTOM
    }
}

