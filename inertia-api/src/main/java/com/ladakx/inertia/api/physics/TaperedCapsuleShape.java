package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;

/**
 * Tapered capsule shape.
 * <p>
 * Height is the full capsule height (not half-height).
 */
public record TaperedCapsuleShape(float height, float topRadius, float bottomRadius) implements PhysicsShape {
    public TaperedCapsuleShape {
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (topRadius <= 0) throw new IllegalArgumentException("topRadius must be > 0");
        if (bottomRadius <= 0) throw new IllegalArgumentException("bottomRadius must be > 0");
    }

    @Override
    public @NotNull Kind kind() {
        return Kind.TAPERED_CAPSULE;
    }
}

