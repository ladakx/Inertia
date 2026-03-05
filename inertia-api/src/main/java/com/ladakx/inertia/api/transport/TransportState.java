package com.ladakx.inertia.api.transport;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record TransportState(@NotNull Location location,
                             @NotNull Vector linearVelocity,
                             @NotNull Vector angularVelocity,
                             double speedMps,
                             double speedKmh,
                             float engineRpm,
                             int currentGear,
                             boolean grounded,
                             @NotNull TransportInput input,
                             @Nullable TrackedInput trackedInput) {

    public TransportState {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(linearVelocity, "linearVelocity");
        Objects.requireNonNull(angularVelocity, "angularVelocity");
        Objects.requireNonNull(input, "input");
    }
}
