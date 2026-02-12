package com.ladakx.inertia.rendering;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

final class TransformSliceProcessor {

    @FunctionalInterface
    interface MetadataDropPolicy {
        boolean shouldDrop(boolean critical, LodLevel lodLevel, int visualId);
    }

    @FunctionalInterface
    interface LodResolver {
        LodLevel resolve(double distanceSq);
    }

    private final Map<Integer, TrackedVisual> visualsById;
    private final RenderNetworkBudgetScheduler scheduler;
    private final VisibilitySliceProcessor.PacketBuffer packetBuffer;
    private final java.util.function.IntToLongFunction tokenProvider;

    TransformSliceProcessor(Map<Integer, TrackedVisual> visualsById,
                            RenderNetworkBudgetScheduler scheduler,
                            VisibilitySliceProcessor.PacketBuffer packetBuffer,
                            java.util.function.IntToLongFunction tokenProvider) {
        this.visualsById = Objects.requireNonNull(visualsById, "visualsById");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packetBuffer = Objects.requireNonNull(packetBuffer, "packetBuffer");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    }

    void runSlice(UUID playerId,
                  PlayerTrackingState trackingState,
                  double viewDistanceSquared,
                  int maxTransformChecksPerPlayerPerTick,
                  long tickCounter,
                  float posThresholdSq,
                  float rotThresholdDot,
                  float midPosThresholdSq,
                  float midRotThresholdDot,
                  int midUpdateIntervalTicks,
                  float farPosThresholdSq,
                  float farRotThresholdDot,
                  int farUpdateIntervalTicks,
                  boolean farAllowMetadataUpdates,
                  SheddingState sheddingState,
                  LodResolver lodResolver,
                  MetadataDropPolicy dropPolicy,
                  AtomicLong droppedUpdates,
                  AtomicLong lodSkippedUpdates,
                  AtomicLong lodSkippedMetadataUpdates,
                  AtomicLong transformChecksSkippedDueBudget) {
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

            int checkBudget = maxTransformChecksPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxTransformChecksPerPlayerPerTick;
            int checked = 0;
            while (checked < checkBudget) {
                Integer id = trackingState.nextVisibleId();
                if (id == null) {
                    trackingState.resetVisibleCursor();
                    break;
                }

                TrackedVisual tracked = visualsById.get(id);
                if (tracked == null) {
                    trackingState.removeVisible(id);
                    checked++;
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
                if (!inRange) {
                    if (trackingState.removeVisible(id)) {
                        scheduler.enqueueDestroy(() -> packetBuffer.buffer(playerId, tracked.visual().createDestroyPacket(), PacketPriority.DESTROY,
                                null, false, false, -1L, -1L));
                    }
                    checked++;
                    continue;
                }

                LodLevel lodLevel = lodResolver.resolve(distSq);
                int midInterval = midUpdateIntervalTicks * sheddingState.midTeleportIntervalMultiplier();
                int farInterval = farUpdateIntervalTicks * sheddingState.farTeleportIntervalMultiplier();
                UpdateDecision updateDecision = tracked.prepareUpdate(
                        lodLevel,
                        tickCounter,
                        posThresholdSq,
                        rotThresholdDot,
                        midPosThresholdSq,
                        midRotThresholdDot,
                        midInterval,
                        farPosThresholdSq,
                        farRotThresholdDot,
                        farInterval
                );
                if (!updateDecision.positionSent() && lodLevel != LodLevel.NEAR && tracked.hasSignificantNearChange(posThresholdSq, rotThresholdDot)) {
                    lodSkippedUpdates.incrementAndGet();
                }

                Object positionPacket = updateDecision.positionSent() ? tracked.getPendingPositionPacket() : null;
                Object transformMetaPacket = updateDecision.transformMetadataSent() ? tracked.getPendingTransformMetaPacket() : null;
                PendingMetadata meta = tracked.getPendingMetaPacket();
                boolean metadataBundledWithPosition = false;
                if (positionPacket != null) {
                    long tokenVersion = tokenProvider.applyAsLong(tracked.visual().getId());
                    ArrayList<Object> bundledPackets = null;
                    if (transformMetaPacket != null) {
                        bundledPackets = new ArrayList<>(3);
                        bundledPackets.add(positionPacket);
                        bundledPackets.add(transformMetaPacket);
                        transformMetaPacket = null;
                    }
                    if (meta != null) {
                        if (bundledPackets == null) {
                            bundledPackets = new ArrayList<>(2);
                            bundledPackets.add(positionPacket);
                        }
                        bundledPackets.add(meta.packet());
                        metadataBundledWithPosition = true;
                    }
                    Object packetToSend = bundledPackets != null ? bundledPackets : positionPacket;
                    packetBuffer.buffer(playerId, packetToSend, PacketPriority.TELEPORT, tracked.visual().getId(), true, false, -1L, tokenVersion);
                    tracked.markSent(player);
                }

                if (transformMetaPacket != null) {
                    long tokenVersion = tokenProvider.applyAsLong(tracked.visual().getId());
                    Object finalTransformMetaPacket = transformMetaPacket;
                    scheduler.enqueueMetadataCoalesced(tracked.visual().getId(),
                            () -> packetBuffer.buffer(playerId, finalTransformMetaPacket, PacketPriority.METADATA, tracked.visual().getId(), false,
                                    updateDecision.transformMetadataForced(), -1L, tokenVersion));
                }

                if (meta != null && !metadataBundledWithPosition) {
                    boolean allowMetaPacket = lodLevel != LodLevel.FAR || farAllowMetadataUpdates;
                    if (allowMetaPacket) {
                        if (dropPolicy.shouldDrop(meta.critical(), lodLevel, tracked.visual().getId())) {
                            droppedUpdates.incrementAndGet();
                            if (lodLevel != LodLevel.NEAR) {
                                lodSkippedMetadataUpdates.incrementAndGet();
                            }
                        } else {
                            long tokenVersion = tokenProvider.applyAsLong(tracked.visual().getId());
                            scheduler.enqueueMetadataCoalesced(tracked.visual().getId(),
                                    () -> packetBuffer.buffer(playerId, meta.packet(), PacketPriority.METADATA, tracked.visual().getId(), false,
                                            meta.critical(), -1L, tokenVersion));
                        }
                    } else {
                        lodSkippedMetadataUpdates.incrementAndGet();
                    }
                }

                checked++;
            }

            if (checkBudget != Integer.MAX_VALUE) {
                int remaining = trackingState.remainingVisibleChecks();
                if (remaining > 0) {
                    transformChecksSkippedDueBudget.addAndGet(remaining);
                }
            }
        } finally {
            trackingState.markTransformTaskDone();
        }
    }
}

