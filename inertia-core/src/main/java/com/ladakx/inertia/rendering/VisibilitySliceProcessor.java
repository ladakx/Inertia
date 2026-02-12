package com.ladakx.inertia.rendering;

import org.bukkit.Location;
import org.bukkit.World;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

final class VisibilitySliceProcessor {

    @FunctionalInterface
    interface PacketBuffer {
        void buffer(UUID playerId,
                    Object packet,
                    PacketPriority priority,
                    Integer visualId,
                    boolean canCoalesce,
                    boolean criticalMetadata,
                    long destroyRegisteredAtTick,
                    long tokenVersion);
    }

    private final Map<Integer, TrackedVisual> visualsById;
    private final RenderNetworkBudgetScheduler scheduler;
    private final PacketBuffer packetBuffer;
    private final java.util.function.IntToLongFunction tokenProvider;
    private final Function<UUID, PlayerFrame> playerFrameProvider;

    VisibilitySliceProcessor(Map<Integer, TrackedVisual> visualsById,
                             RenderNetworkBudgetScheduler scheduler,
                             PacketBuffer packetBuffer,
                             java.util.function.IntToLongFunction tokenProvider,
                             Function<UUID, PlayerFrame> playerFrameProvider) {
        this.visualsById = Objects.requireNonNull(visualsById, "visualsById");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packetBuffer = Objects.requireNonNull(packetBuffer, "packetBuffer");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
        this.playerFrameProvider = Objects.requireNonNull(playerFrameProvider, "playerFrameProvider");
    }

    void runSlice(UUID playerId,
                  PlayerTrackingState trackingState,
                  double viewDistanceSquared,
                  int maxVisibilityUpdatesPerPlayerPerTick,
                  boolean destroyDrainFastPathActive,
                  Runnable rescheduleIfNeeded) {
        try {
            PlayerFrame playerFrame = playerFrameProvider.apply(playerId);
            if (playerFrame == null) {
                trackingState.clearVisible();
                return;
            }
            UUID playerWorldId = playerFrame.worldId();

            int budget = maxVisibilityUpdatesPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxVisibilityUpdatesPerPlayerPerTick;
            int processed = 0;
            while (processed < budget) {
                Integer id = trackingState.nextCandidateId();
                if (id == null) {
                    trackingState.markVisibilityPassComplete();
                    break;
                }

                TrackedVisual tracked = visualsById.get(id);
                if (tracked == null) {
                    trackingState.removeVisible(id);
                    processed++;
                    continue;
                }

                Location trackedLoc = tracked.location();
                World trackedWorld = trackedLoc.getWorld();
                boolean sameWorld = trackedWorld != null && playerWorldId.equals(trackedWorld.getUID());

                double dx = trackedLoc.getX() - playerFrame.x();
                double dy = trackedLoc.getY() - playerFrame.y();
                double dz = trackedLoc.getZ() - playerFrame.z();
                double distSq = dx * dx + dy * dy + dz * dz;

                boolean inRange = sameWorld && distSq <= viewDistanceSquared;

                if (inRange) {
                    if (trackingState.addVisible(id)) {
                        Location spawnLoc = tracked.location().clone();
                        Quaternionf spawnRot = new Quaternionf(tracked.rotation());
                        long tokenVersion = tokenProvider.applyAsLong(id);
                        int visualId = tracked.visual().getId();
                        scheduler.enqueueSpawn(() ->
                                        packetBuffer.buffer(playerId, tracked.visual().createSpawnPacket(spawnLoc, spawnRot), PacketPriority.SPAWN,
                                                visualId, false, false, -1L, tokenVersion),
                                visualId,
                                tokenVersion
                        );
                        tracked.markSent();
                    }
                } else if (trackingState.removeVisible(id)) {
                    scheduler.enqueueDestroy(() -> packetBuffer.buffer(playerId, tracked.visual().createDestroyPacket(), PacketPriority.DESTROY,
                            null, false, false, -1L, -1L));
                }

                processed++;
            }
        } finally {
            trackingState.markVisibilityTaskDone();
            if (!destroyDrainFastPathActive && trackingState.needsVisibilityPass() && rescheduleIfNeeded != null) {
                rescheduleIfNeeded.run();
            }
        }
    }
}
