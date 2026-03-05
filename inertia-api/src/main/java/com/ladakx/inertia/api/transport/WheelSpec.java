package com.ladakx.inertia.api.transport;

import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record WheelSpec(@NotNull String key,
                        @NotNull Vector3f position,
                        @NotNull Vector3f suspensionDirection,
                        float radius,
                        float width,
                        float suspensionMinLength,
                        float suspensionMaxLength,
                        float suspensionFrequency,
                        float suspensionDamping,
                        float maxSteerAngleRad,
                        float maxBrakeTorque,
                        float maxHandBrakeTorque) {

    public WheelSpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(suspensionDirection, "suspensionDirection");
        if (key.isBlank()) throw new IllegalArgumentException("key cannot be blank");
        if (radius <= 0f) throw new IllegalArgumentException("radius must be > 0");
        if (width <= 0f) throw new IllegalArgumentException("width must be > 0");
        if (suspensionMinLength < 0f) throw new IllegalArgumentException("suspensionMinLength must be >= 0");
        if (suspensionMaxLength < suspensionMinLength) {
            throw new IllegalArgumentException("suspensionMaxLength must be >= suspensionMinLength");
        }
        if (suspensionFrequency < 0f) throw new IllegalArgumentException("suspensionFrequency must be >= 0");
        if (suspensionDamping < 0f) throw new IllegalArgumentException("suspensionDamping must be >= 0");
        if (maxSteerAngleRad < 0f) throw new IllegalArgumentException("maxSteerAngleRad must be >= 0");
        if (maxBrakeTorque < 0f) throw new IllegalArgumentException("maxBrakeTorque must be >= 0");
        if (maxHandBrakeTorque < 0f) throw new IllegalArgumentException("maxHandBrakeTorque must be >= 0");
    }
}
