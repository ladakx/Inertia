package com.ladakx.inertia.api.transport;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.Objects;

public record WheelWorldPose(int wheelIndex,
                             @NotNull Vector position,
                             @NotNull Quaternionf rotation) {
    public WheelWorldPose {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
    }
}
