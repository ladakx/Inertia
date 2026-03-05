package com.ladakx.inertia.api.transport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record TransportSpec(@Nullable TransportId id,
                            @NotNull TransportType type,
                            @NotNull ChassisSpec chassis,
                            @NotNull CollisionTesterSpec collisionTester,
                            @NotNull EngineSpec engine,
                            @NotNull TransmissionSpec transmission,
                            @NotNull List<WheelSpec> wheels,
                            @NotNull List<DifferentialSpec> differentials,
                            @NotNull List<AntiRollBarSpec> antiRollBars) {

    public TransportSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(chassis, "chassis");
        Objects.requireNonNull(collisionTester, "collisionTester");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(transmission, "transmission");
        Objects.requireNonNull(wheels, "wheels");
        Objects.requireNonNull(differentials, "differentials");
        Objects.requireNonNull(antiRollBars, "antiRollBars");
        if (wheels.isEmpty()) {
            throw new IllegalArgumentException("wheels cannot be empty");
        }
        wheels = Collections.unmodifiableList(List.copyOf(wheels));
        differentials = Collections.unmodifiableList(List.copyOf(differentials));
        antiRollBars = Collections.unmodifiableList(List.copyOf(antiRollBars));
    }
}
