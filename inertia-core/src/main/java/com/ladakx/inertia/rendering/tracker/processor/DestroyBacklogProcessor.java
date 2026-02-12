package com.ladakx.inertia.rendering.tracker.processor;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.rendering.tracker.budget.RenderNetworkBudgetScheduler;
import com.ladakx.inertia.rendering.tracker.packet.PacketPriority;
import com.ladakx.inertia.rendering.tracker.state.PendingDestroyState;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DestroyBacklogProcessor {

    public record Metrics(int pendingDestroyIdsBacklog,
                          int destroyQueueDepthBacklog,
                          boolean destroyDrainFastPathActive,
                          long oldestQueueAgeMillis,
                          long destroyBacklogAgeMillis) {}

    @FunctionalInterface
    public interface PacketBuffer {
        void buffer(UUID playerId, Object packet, PacketPriority priority, long destroyRegisteredAtTick);
    }

    @FunctionalInterface
    public interface PreFlushBulkDestroy {
        void preFlush(UUID playerId, int[] visualIds);
    }

    private final Map<UUID, PendingDestroyState> pendingDestroyIds;
    private final RenderNetworkBudgetScheduler scheduler;
    private final PacketFactory packetFactory;
    private final PacketBuffer packetBuffer;
    private final PreFlushBulkDestroy preFlushBulkDestroy;

    public DestroyBacklogProcessor(Map<UUID, PendingDestroyState> pendingDestroyIds,
                            RenderNetworkBudgetScheduler scheduler,
                            PacketFactory packetFactory,
                            PacketBuffer packetBuffer,
                            PreFlushBulkDestroy preFlushBulkDestroy) {
        this.pendingDestroyIds = Objects.requireNonNull(pendingDestroyIds, "pendingDestroyIds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packetFactory = Objects.requireNonNull(packetFactory, "packetFactory");
        this.packetBuffer = Objects.requireNonNull(packetBuffer, "packetBuffer");
        this.preFlushBulkDestroy = Objects.requireNonNull(preFlushBulkDestroy, "preFlushBulkDestroy");
    }

    public void enqueuePendingDestroyTasks() {
        if (pendingDestroyIds.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingDestroyState>> iterator = pendingDestroyIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingDestroyState> entry = iterator.next();
            UUID playerId = entry.getKey();
            PendingDestroyState pendingState = entry.getValue();
            iterator.remove();
            IntOpenHashSet ids = pendingState != null ? pendingState.ids() : null;

            if (ids == null || ids.isEmpty()) {
                continue;
            }

            int[] idArray = ids.toIntArray();
            if (idArray.length == 0) {
                continue;
            }

            long registeredAtTick = pendingState.firstUnregisterTick();

            if (idArray.length == 1) {
                int targetId = idArray[0];
                scheduler.enqueueDestroy(() ->
                        packetBuffer.buffer(playerId, packetFactory.createDestroyPacket(targetId), PacketPriority.DESTROY, registeredAtTick)
                );
                continue;
            }

            scheduler.enqueueDestroy(() -> {
                preFlushBulkDestroy.preFlush(playerId, idArray);
                try {
                    packetBuffer.buffer(playerId, packetFactory.createDestroyPacket(idArray), PacketPriority.DESTROY, registeredAtTick);
                } catch (Throwable ignored) {
                    for (int id : idArray) {
                        packetBuffer.buffer(playerId, packetFactory.createDestroyPacket(id), PacketPriority.DESTROY, registeredAtTick);
                    }
                }
            });
        }
    }

    public Metrics refreshMetrics(int destroyBacklogThreshold) {
        long now = System.nanoTime();
        int pendingBacklog = calculatePendingDestroyBacklog();
        int queueBacklog = scheduler.getDestroyQueueDepth();

        long pendingAgeNanos = 0L;
        for (PendingDestroyState state : pendingDestroyIds.values()) {
            if (state == null || state.backlogSinceNanos() <= 0L) {
                continue;
            }
            pendingAgeNanos = Math.max(pendingAgeNanos, Math.max(0L, now - state.backlogSinceNanos()));
        }
        long destroyQueueAgeNanos = scheduler.getDestroyQueueOldestAgeMillis() * 1_000_000L;
        long destroyBacklogAgeMillis = Math.max(pendingAgeNanos, destroyQueueAgeNanos) / 1_000_000L;

        int threshold = Math.max(1, destroyBacklogThreshold);
        boolean destroyDrainFastPathActive = pendingBacklog >= threshold || queueBacklog >= threshold;

        return new Metrics(
                pendingBacklog,
                queueBacklog,
                destroyDrainFastPathActive,
                scheduler.getOldestQueueAgeMillis(),
                destroyBacklogAgeMillis
        );
    }

    private int calculatePendingDestroyBacklog() {
        int total = 0;
        for (PendingDestroyState pendingState : pendingDestroyIds.values()) {
            if (pendingState == null || pendingState.ids().isEmpty()) {
                continue;
            }
            total += pendingState.ids().size();
        }
        return total;
    }
}
