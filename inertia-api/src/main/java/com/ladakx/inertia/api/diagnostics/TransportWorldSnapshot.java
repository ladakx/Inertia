package com.ladakx.inertia.api.diagnostics;

import java.util.Objects;
import java.util.UUID;

public record TransportWorldSnapshot(UUID worldId,
                                     String worldName,
                                     int transports,
                                     int activeTransports,
                                     int groundedTransports,
                                     double averageSpeedKmh,
                                     double maxSpeedKmh,
                                     double averageEngineRpm) {
    public TransportWorldSnapshot {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
    }
}
