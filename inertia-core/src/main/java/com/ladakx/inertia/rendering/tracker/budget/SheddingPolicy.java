package com.ladakx.inertia.rendering.tracker.budget;

public final class SheddingPolicy {

    public SheddingState compute(RenderNetworkBudgetScheduler scheduler,
                                 int destroyBacklogThreshold,
                                 int pendingDestroyIdsBacklog,
                                 int destroyQueueDepthBacklog,
                                 boolean destroyDrainFastPathActive) {
        int metadataDepth = scheduler.getMetadataQueueDepth();
        int totalDepth = scheduler.queueDepth();
        int destroyDepth = destroyQueueDepthBacklog + pendingDestroyIdsBacklog;
        int threshold = Math.max(1, destroyBacklogThreshold);

        int intensity = 0;
        if (totalDepth > threshold) intensity++;
        if (metadataDepth > threshold / 2) intensity++;
        if (destroyDepth > threshold) intensity++;
        if (destroyDrainFastPathActive) intensity += 2;

        intensity = Math.min(4, intensity);
        return SheddingState.of(intensity);
    }
}
