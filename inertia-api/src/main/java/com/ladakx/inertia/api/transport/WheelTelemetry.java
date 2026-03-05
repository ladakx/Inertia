package com.ladakx.inertia.api.transport;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public record WheelTelemetry(int wheelIndex,
                             boolean hasContact,
                             int contactBodyId,
                             float steerAngleRad,
                             float suspensionLambda,
                             float longitudinalLambda,
                             float lateralLambda,
                             float angularVelocity,
                             float rotationAngleRad,
                             @Nullable Vector contactPosition) {
}
