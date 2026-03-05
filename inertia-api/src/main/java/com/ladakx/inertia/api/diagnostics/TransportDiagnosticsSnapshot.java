package com.ladakx.inertia.api.diagnostics;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record TransportDiagnosticsSnapshot(int totalTransports,
                                           int activeTransports,
                                           int groundedTransports,
                                           double averageSpeedKmh,
                                           double maxSpeedKmh,
                                           Collection<TransportWorldSnapshot> worldSnapshots,
                                           long sampleTimestampNanos) {
    public TransportDiagnosticsSnapshot {
        Objects.requireNonNull(worldSnapshots, "worldSnapshots");
        worldSnapshots = List.copyOf(worldSnapshots);
    }
}
