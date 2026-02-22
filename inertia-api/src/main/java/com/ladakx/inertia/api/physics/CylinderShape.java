package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;

/**
 * Cylinder shape.
 * <p>
 * Height is the full cylinder height (not half-height).
 */
public record CylinderShape(float height, float radius, float convexRadius) implements PhysicsShape {
    public CylinderShape {
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (radius <= 0) throw new IllegalArgumentException("radius must be > 0");
    }

    public CylinderShape(float height, float radius) {
        this(height, radius, -1f);
    }

    @Override
    public @NotNull Kind kind() {
        return Kind.CYLINDER;
    }
}

