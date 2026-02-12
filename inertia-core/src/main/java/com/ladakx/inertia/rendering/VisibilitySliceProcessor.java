package com.ladakx.inertia.rendering;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    VisibilitySliceProcessor(Map<Integer, TrackedVisual> visualsById,
                             RenderNetworkBudgetScheduler scheduler,
                             PacketBuffer packetBuffer,
                             java.util.function.IntToLongFunction tokenProvider) {
        this.visualsById = Objects.requireNonNull(visualsById, "visualsById");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packetBuffer = Objects.requireNonNull(packetBuffer, "packetBuffer");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    }

    void runSlice(UUID playerId,
                  PlayerTrackingState trackingState,
                  double viewDistanceSquared,
                  int maxVisibilityUpdatesPerPlayerPerTick,
                  boolean destroyDrainFastPathActive,
                  Runnable rescheduleIfNeeded) {
        try {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                trackingState.clearVisible();
                return;
            }

            Location playerLoc = player.getLocation();
            World playerWorld = playerLoc.getWorld();
            if (playerWorld == null) {
                return;
            }
            UUID playerWorldId = playerWorld.getUID();

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

                double dx = trackedLoc.getX() - playerLoc.getX();
                double dy = trackedLoc.getY() - playerLoc.getY();
                double dz = trackedLoc.getZ() - playerLoc.getZ();
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
                        tracked.markSent(player);
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
