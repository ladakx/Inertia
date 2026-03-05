package com.ladakx.inertia.api.transport;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.Objects;

public record ChassisPose(@NotNull Location location,
                          @NotNull Quaternionf rotation) {
    public ChassisPose {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
    }
}
