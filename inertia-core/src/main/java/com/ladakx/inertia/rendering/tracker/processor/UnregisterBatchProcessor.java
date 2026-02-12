package com.ladakx.inertia.rendering.tracker.processor;

import com.ladakx.inertia.rendering.tracker.budget.RenderNetworkBudgetScheduler;
import com.ladakx.inertia.rendering.tracker.grid.ChunkGridIndex;
import com.ladakx.inertia.rendering.tracker.registry.VisualTombstoneService;
import com.ladakx.inertia.rendering.tracker.state.PendingDestroyState;
import com.ladakx.inertia.rendering.tracker.state.PlayerTrackingState;
import com.ladakx.inertia.rendering.tracker.state.TrackedVisual;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class UnregisterBatchProcessor {

    public record Result(boolean bulkFastPathUsed, int removedCount) {}

    @FunctionalInterface
    public interface VisualQueueInvalidator {
        long invalidateVisualQueues(int visualId);
    }

    private final Map<Integer, TrackedVisual> visualsById;
    private final ChunkGridIndex chunkGrid;
    private final VisualTombstoneService tombstoneService;
    private final RenderNetworkBudgetScheduler scheduler;
    private final Map<UUID, PlayerTrackingState> playerTrackingStates;
    private final Map<UUID, PendingDestroyState> pendingDestroyIds;
    private final VisualQueueInvalidator queueInvalidator;
    private final long tombstoneTtlTicks;

    public UnregisterBatchProcessor(Map<Integer, TrackedVisual> visualsById,
                             ChunkGridIndex chunkGrid,
                             VisualTombstoneService tombstoneService,
                             RenderNetworkBudgetScheduler scheduler,
                             Map<UUID, PlayerTrackingState> playerTrackingStates,
                             Map<UUID, PendingDestroyState> pendingDestroyIds,
                             VisualQueueInvalidator queueInvalidator,
                             long tombstoneTtlTicks) {
        this.visualsById = Objects.requireNonNull(visualsById, "visualsById");
        this.chunkGrid = Objects.requireNonNull(chunkGrid, "chunkGrid");
        this.tombstoneService = Objects.requireNonNull(tombstoneService, "tombstoneService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.playerTrackingStates = Objects.requireNonNull(playerTrackingStates, "playerTrackingStates");
        this.pendingDestroyIds = Objects.requireNonNull(pendingDestroyIds, "pendingDestroyIds");
        this.queueInvalidator = Objects.requireNonNull(queueInvalidator, "queueInvalidator");
        this.tombstoneTtlTicks = Math.max(0L, tombstoneTtlTicks);
    }

    public Result unregisterBatch(Collection<Integer> ids, long tickCounter) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            return new Result(false, 0);
        }

        IntArrayList removedIds = new IntArrayList(ids.size());

        for (Integer id : ids) {
            if (id == null) {
                continue;
            }

            int visualId = id.intValue();
            long activeTokenVersion = queueInvalidator.invalidateVisualQueues(visualId);
            scheduler.invalidateVisual(visualId, activeTokenVersion);

            TrackedVisual tracked = visualsById.remove(id);
            if (tracked == null) {
                continue;
            }

            chunkGrid.remove(id, tracked.location());
            tombstoneService.add(id, tickCounter + tombstoneTtlTicks);
            removedIds.add(visualId);
        }

        if (removedIds.isEmpty()) {
            return new Result(false, 0);
        }

        IntOpenHashSet removedSet = new IntOpenHashSet(removedIds);
        boolean useBulkFastPath = removedIds.size() >= 64;

        for (Map.Entry<UUID, PlayerTrackingState> entry : playerTrackingStates.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerTrackingState trackingState = entry.getValue();
            Set<Integer> visible = trackingState.visibleIds();

            IntArrayList hiddenForPlayer = null;
            if (useBulkFastPath) {
                Iterator<Integer> iterator = visible.iterator();
                while (iterator.hasNext()) {
                    Integer visibleId = iterator.next();
                    if (visibleId == null || !removedSet.contains(visibleId.intValue())) {
                        continue;
                    }
                    iterator.remove();
                    trackingState.markVisibleIterationDirty();
                    if (hiddenForPlayer == null) {
                        hiddenForPlayer = new IntArrayList();
                    }
                    hiddenForPlayer.add(visibleId.intValue());
                }
            } else {
                for (int i = 0; i < removedIds.size(); i++) {
                    int removedId = removedIds.getInt(i);
                    if (visible.remove(removedId)) {
                        trackingState.markVisibleIterationDirty();
                        if (hiddenForPlayer == null) {
                            hiddenForPlayer = new IntArrayList();
                        }
                        hiddenForPlayer.add(removedId);
                    }
                }
            }

            if (hiddenForPlayer != null && !hiddenForPlayer.isEmpty()) {
                PendingDestroyState queue = pendingDestroyIds.computeIfAbsent(playerId, ignored -> new PendingDestroyState());
                if (queue.ids().isEmpty()) {
                    queue.markBacklogStart(System.nanoTime(), tickCounter);
                }
                queue.addAll(hiddenForPlayer);
            }
        }

        return new Result(useBulkFastPath, removedIds.size());
    }
}
