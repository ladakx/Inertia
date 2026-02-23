package com.ladakx.inertia.api.diagnostics;

import java.util.Objects;
import java.util.UUID;

public record WorldHealthSnapshot(UUID worldId,
                                  String worldName,
                                  boolean overloaded,
                                  TickDurationPercentiles tickDurationPercentiles,
                                  QueueBackpressureCounters queueBackpressureCounters,
                                  BodyCounters bodyCounters,
                                  long sampleTick,
                                  long sampleTimestampNanos) {
    public WorldHealthSnapshot {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(tickDurationPercentiles, "tickDurationPercentiles");
        Objects.requireNonNull(queueBackpressureCounters, "queueBackpressureCounters");
        Objects.requireNonNull(bodyCounters, "bodyCounters");
    }
}
