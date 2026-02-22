package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;

public record SphereShape(float radius) implements PhysicsShape {
    public SphereShape {
        if (radius <= 0) throw new IllegalArgumentException("radius must be > 0");
    }

    @Override
    public @NotNull Kind kind() {
        return Kind.SPHERE;
    }
}

