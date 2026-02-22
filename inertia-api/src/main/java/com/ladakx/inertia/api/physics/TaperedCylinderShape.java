package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;

/**
 * Tapered cylinder shape.
 * <p>
 * Height is the full cylinder height (not half-height).
 */
public record TaperedCylinderShape(float height, float topRadius, float bottomRadius, float convexRadius) implements PhysicsShape {
    public TaperedCylinderShape {
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (topRadius <= 0) throw new IllegalArgumentException("topRadius must be > 0");
        if (bottomRadius <= 0) throw new IllegalArgumentException("bottomRadius must be > 0");
    }

    public TaperedCylinderShape(float height, float topRadius, float bottomRadius) {
        this(height, topRadius, bottomRadius, -1f);
    }

    @Override
    public @NotNull Kind kind() {
        return Kind.TAPERED_CYLINDER;
    }
}

