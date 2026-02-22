package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;

/**
 * Capsule shape.
 * <p>
 * Height is the full cylinder height (not half-height).
 */
public record CapsuleShape(float height, float radius) implements PhysicsShape {
    public CapsuleShape {
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (radius <= 0) throw new IllegalArgumentException("radius must be > 0");
    }

    @Override
    public @NotNull Kind kind() {
        return Kind.CAPSULE;
    }
}

