package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * One child shape instance inside a {@link CompoundShape}.
 */
public record ShapeInstance(@NotNull PhysicsShape shape,
                            @NotNull Vector3f position,
                            @NotNull Quaternionf rotation,
                            @Nullable Vector3f centerOfMassOffset) {
    public ShapeInstance {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
    }
}

