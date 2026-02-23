package com.ladakx.inertia.api.diagnostics;

public record MetricsServiceSnapshot(int oneTimeQueueDepth,
                                     int recurringQueueDepth,
                                     long droppedSnapshots,
                                     long overwrittenSnapshots,
                                     int activeBodyCount,
                                     int totalBodyCount,
                                     int staticBodyCount,
                                     int maxBodyLimit) {
}
