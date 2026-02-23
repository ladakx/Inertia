package com.ladakx.inertia.physics.world.loop;

import java.util.Objects;

public interface LoopTickListener {
    void onTickStart(long tickNumber);

    void onTickEnd(long tickNumber,
                   long durationNanos,
                   int activeBodies,
                   int totalBodies,
                   int staticBodies,
                   int maxBodies,
                   long droppedSnapshots,
                   long overwrittenSnapshots);

    default void onDiagnostics(LoopDiagnosticsSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
