package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.physics.PhysicsBodySpec;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record ChassisSpec(@NotNull PhysicsBodySpec bodySpec) {
    public ChassisSpec {
        Objects.requireNonNull(bodySpec, "bodySpec");
    }
}
