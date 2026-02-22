package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ConvexHullShape(@NotNull List<Vector3f> points, float convexRadius) implements PhysicsShape {
    public ConvexHullShape {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) throw new IllegalArgumentException("points cannot be empty");
        points = Collections.unmodifiableList(List.copyOf(points));
    }

    public ConvexHullShape(@NotNull List<Vector3f> points) {
        this(points, -1f);
    }

    @Override
    public @NotNull Kind kind() {
        return Kind.CONVEX_HULL;
    }
}

