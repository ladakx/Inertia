package com.ladakx.inertia.api.diagnostics;

public record QueueBackpressureCounters(int pendingSnapshots,
                                        long droppedSnapshots,
                                        long overwrittenSnapshots,
                                        long backlogTicks,
                                        long overloadedTicks,
                                        int oneTimeQueueDepth,
                                        int recurringQueueDepth) {
}
