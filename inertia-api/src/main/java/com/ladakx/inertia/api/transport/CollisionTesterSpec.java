package com.ladakx.inertia.api.transport;

import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record CollisionTesterSpec(@NotNull CollisionTesterType type,
                                  int objectLayer,
                                  @NotNull Vector3f up,
                                  float maxSlopeAngleRad) {
    public CollisionTesterSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(up, "up");
        if (objectLayer < 0) {
            throw new IllegalArgumentException("objectLayer must be >= 0");
        }
        if (maxSlopeAngleRad <= 0f) {
            throw new IllegalArgumentException("maxSlopeAngleRad must be > 0");
        }
    }

    public static @NotNull CollisionTesterSpec ray(int objectLayer) {
        return new CollisionTesterSpec(CollisionTesterType.RAY, objectLayer, new Vector3f(0f, 1f, 0f), (float) Math.toRadians(80d));
    }
}
