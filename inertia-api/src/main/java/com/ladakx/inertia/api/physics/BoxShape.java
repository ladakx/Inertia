package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;

/**
 * Box shape defined by half-extents.
 */
public record BoxShape(float halfX, float halfY, float halfZ, float convexRadius) implements PhysicsShape {
    public BoxShape {
        if (halfX <= 0 || halfY <= 0 || halfZ <= 0) {
            throw new IllegalArgumentException("half-extents must be > 0");
        }
    }

    public BoxShape(float halfX, float halfY, float halfZ) {
        this(halfX, halfY, halfZ, -1f);
    }

    @Override
    public @NotNull Kind kind() {
        return Kind.BOX;
    }
}

