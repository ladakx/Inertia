package com.ladakx.inertia.physics.world.loop;

import java.util.Objects;
import java.util.UUID;

public record LoopDiagnosticsSnapshot(UUID worldId,
                                      String worldName,
                                      long tickNumber,
                                      long durationNanos,
                                      int configuredTps,
                                      int effectiveTps,
                                      int pendingSnapshots,
                                      boolean backlog,
                                      long backlogTicks,
                                      boolean overloaded,
                                      long overloadedTicks,
                                      int activeBodies,
                                      int totalBodies,
                                      int staticBodies,
                                      int maxBodies,
                                      long droppedSnapshots,
                                      long overwrittenSnapshots,
                                      long sampleTimestampNanos) {
    public LoopDiagnosticsSnapshot {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
    }
}
