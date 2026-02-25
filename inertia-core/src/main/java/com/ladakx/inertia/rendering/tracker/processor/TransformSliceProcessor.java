package com.ladakx.inertia.rendering.tracker.processor;

import com.ladakx.inertia.rendering.tracker.budget.RenderNetworkBudgetScheduler;
import com.ladakx.inertia.rendering.tracker.packet.PacketPriority;
import com.ladakx.inertia.rendering.tracker.state.LodLevel;
import com.ladakx.inertia.rendering.tracker.budget.SheddingState;
import com.ladakx.inertia.rendering.tracker.state.PendingMetadata;
import com.ladakx.inertia.rendering.tracker.state.PlayerFrame;
import com.ladakx.inertia.rendering.tracker.state.PlayerTrackingState;
import com.ladakx.inertia.rendering.tracker.state.TrackedVisual;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class TransformSliceProcessor {

    @FunctionalInterface
    public interface MetadataDropPolicy {
        boolean shouldDrop(boolean critical, LodLevel lodLevel, int visualId);
    }

    @FunctionalInterface
    public interface LodResolver {
        LodLevel resolve(double distanceSq);
    }

    @FunctionalInterface
    public interface GroupMembersProvider {
        int[] membersOf(int groupKey);
    }

    private final Map<Integer, TrackedVisual> visualsById;
    private final RenderNetworkBudgetScheduler scheduler;
    private final VisibilitySliceProcessor.PacketBuffer packetBuffer;
    private final java.util.function.IntToLongFunction tokenProvider;
    private final Function<UUID, PlayerFrame> playerFrameProvider;
    private final GroupMembersProvider groupMembersProvider;

    public TransformSliceProcessor(Map<Integer, TrackedVisual> visualsById,
                            RenderNetworkBudgetScheduler scheduler,
                            VisibilitySliceProcessor.PacketBuffer packetBuffer,
                            java.util.function.IntToLongFunction tokenProvider,
                            Function<UUID, PlayerFrame> playerFrameProvider,
                            GroupMembersProvider groupMembersProvider) {
        this.visualsById = Objects.requireNonNull(visualsById, "visualsById");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packetBuffer = Objects.requireNonNull(packetBuffer, "packetBuffer");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
        this.playerFrameProvider = Objects.requireNonNull(playerFrameProvider, "playerFrameProvider");
        this.groupMembersProvider = Objects.requireNonNull(groupMembersProvider, "groupMembersProvider");
    }

    public void runSlice(UUID playerId,
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
            PlayerFrame playerFrame = playerFrameProvider.apply(playerId);
            if (playerFrame == null) {
                trackingState.clearVisible();
                return;
            }
            UUID playerWorldId = playerFrame.worldId();

            int groupBudget = maxTransformChecksPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxTransformChecksPerPlayerPerTick;
            int processedGroups = 0;
            int scanned = 0;
            int totalVisible = trackingState.visibleIds().size();
            HashSet<Integer> processedGroupKeys = new HashSet<>();

            while (processedGroups < groupBudget && scanned < totalVisible) {
                Integer id = trackingState.nextVisibleId(visualId -> {
                    TrackedVisual tracked = visualsById.get(visualId);
                    return tracked != null ? tracked.groupKey() : visualId;
                });
                if (id == null) {
                    trackingState.resetVisibleCursor();
                    break;
                }
                scanned++;

                TrackedVisual anyTracked = visualsById.get(id);
                if (anyTracked == null) {
                    trackingState.removeVisible(id);
                    continue;
                }

                int groupKey = anyTracked.groupKey();
                if (!processedGroupKeys.add(groupKey)) {
                    continue;
                }

                int[] members = groupMembersProvider.membersOf(groupKey);
                if (members == null || members.length == 0) {
                    members = new int[]{id.intValue()};
                }

                TrackedVisual anchor = visualsById.get(groupKey);
                if (anchor == null) {
                    anchor = anyTracked;
                }
                if (anchor != null && anchor.isPassenger()) {
                    for (int memberId : members) {
                        TrackedVisual candidate = visualsById.get(memberId);
                        if (candidate == null) continue;
                        if (!candidate.isPassenger()) {
                            anchor = candidate;
                            break;
                        }
                    }
                }

                Location anchorLoc = anchor.location();
                World anchorWorld = anchorLoc.getWorld();
                boolean sameWorld = anchorWorld != null && playerWorldId.equals(anchorWorld.getUID());
                double dx = anchorLoc.getX() - playerFrame.x();
                double dy = anchorLoc.getY() - playerFrame.y();
                double dz = anchorLoc.getZ() - playerFrame.z();
                double distSq = dx * dx + dy * dy + dz * dz;
                boolean inRange = sameWorld && distSq <= viewDistanceSquared;

                LodLevel lodLevel = lodResolver.resolve(distSq);

                boolean groupAllowed = anchor.isAllowedForProtocol(playerFrame.clientProtocol()) && anchor.isEnabled();
                if (!inRange || !groupAllowed) {
                    for (int memberId : members) {
                        if (!trackingState.isVisible(memberId)) continue;
                        TrackedVisual tracked = visualsById.get(memberId);
                        if (tracked == null) {
                            trackingState.removeVisible(memberId);
                            continue;
                        }
                        if (trackingState.removeVisible(memberId)) {
                            TrackedVisual finalTracked = tracked;
                            scheduler.enqueueDestroy(() -> packetBuffer.buffer(playerId, finalTracked.visual().createDestroyPacket(), PacketPriority.DESTROY,
                                    null, false, false, -1L, -1L));
                        }
                    }
                    processedGroups++;
                    continue;
                }

                int midInterval = midUpdateIntervalTicks * sheddingState.midTeleportIntervalMultiplier();
                int farInterval = farUpdateIntervalTicks * sheddingState.farTeleportIntervalMultiplier();

                float activePosThresholdSq;
                float activeRotThresholdDot;
                int activeIntervalTicks;
                if (lodLevel == LodLevel.NEAR) {
                    activePosThresholdSq = posThresholdSq;
                    activeRotThresholdDot = rotThresholdDot;
                    activeIntervalTicks = 0;
                } else if (lodLevel == LodLevel.MID) {
                    activePosThresholdSq = midPosThresholdSq;
                    activeRotThresholdDot = midRotThresholdDot;
                    activeIntervalTicks = midInterval;
                } else {
                    activePosThresholdSq = farPosThresholdSq;
                    activeRotThresholdDot = farRotThresholdDot;
                    activeIntervalTicks = farInterval;
                }

                boolean forcedTransform = false;
                for (int memberId : members) {
                    if (!trackingState.isVisible(memberId)) continue;
                    TrackedVisual tracked = visualsById.get(memberId);
                    if (tracked != null && tracked.isForceTransformResyncDirty()) {
                        forcedTransform = true;
                        break;
                    }
                }

                boolean positionChanged = anchor.isPositionChanged(lodLevel, activePosThresholdSq);
                boolean rotationChanged = anchor.isRotationChanged(lodLevel, activeRotThresholdDot);
                boolean intervalReached = lodLevel == LodLevel.NEAR || anchor.isIntervalReached(lodLevel, tickCounter, activeIntervalTicks);

                boolean sendPosition = lodLevel == LodLevel.NEAR ? positionChanged : positionChanged && intervalReached;
                boolean sendTransform = lodLevel == LodLevel.NEAR
                        ? (rotationChanged || forcedTransform)
                        : (forcedTransform || rotationChanged || intervalReached);

                if (!sendPosition && lodLevel != LodLevel.NEAR && anchor.isPositionChanged(LodLevel.NEAR, posThresholdSq)) {
                    lodSkippedUpdates.incrementAndGet();
                }

                if (sendPosition || sendTransform) {
                    ArrayList<Object> groupPackets = new ArrayList<>(members.length * 2);
                    boolean anyCritical = false;

                    for (int memberId : members) {
                        if (!trackingState.isVisible(memberId)) continue;
                        TrackedVisual tracked = visualsById.get(memberId);
                        if (tracked == null) continue;

                        if (!tracked.isEnabled() || !tracked.isAllowedForLod(lodLevel)
                                || !tracked.isAllowedForProtocol(playerFrame.clientProtocol())) {
                            continue;
                        }

                        if (sendPosition) {
                            if (!tracked.isPassenger()) {
                                Object positionPacket = tracked.ensurePositionPacket();
                                if (positionPacket != null) {
                                    groupPackets.add(positionPacket);
                                }
                            }
                            tracked.syncPositionForLod(lodLevel);
                        }

                        if (sendTransform) {
                            Object transformPacket = tracked.ensureTransformMetaPacket();
                            if (transformPacket != null) {
                                groupPackets.add(transformPacket);
                            }
                            tracked.syncRotationForLod(lodLevel);
                            tracked.clearForceTransformResyncDirty();
                        }

                        PendingMetadata meta = tracked.getPendingMetaPacket();
                        if (meta != null) {
                            if (!dropPolicy.shouldDrop(meta.critical(), lodLevel, tracked.visual().getId())) {
                                groupPackets.add(meta.packet());
                                anyCritical = anyCritical || meta.critical();
                                tracked.clearPendingMetaPacket();
                            } else {
                                droppedUpdates.incrementAndGet();
                                if (lodLevel != LodLevel.NEAR) {
                                    lodSkippedMetadataUpdates.incrementAndGet();
                                }
                            }
                        }

                        if (lodLevel != LodLevel.NEAR && intervalReached) {
                            tracked.markIntervalTick(lodLevel, tickCounter);
                        }
                    }

                    if (!groupPackets.isEmpty()) {
                        long tokenVersion = tokenProvider.applyAsLong(groupKey);
                        packetBuffer.buffer(playerId, groupPackets, PacketPriority.TELEPORT, groupKey, true, anyCritical, -1L, tokenVersion);
                    }
                }

                // Any leftover metadata (not yet cleared) is sent out-of-band.
                for (int memberId : members) {
                    if (!trackingState.isVisible(memberId)) continue;
                    TrackedVisual tracked = visualsById.get(memberId);
                    if (tracked == null) continue;

                    PendingMetadata meta = tracked.getPendingMetaPacket();
                    if (meta == null) continue;

                    boolean allowMetaPacket = lodLevel != LodLevel.FAR || farAllowMetadataUpdates;
                    if (!allowMetaPacket) {
                        lodSkippedMetadataUpdates.incrementAndGet();
                        continue;
                    }
                    if (dropPolicy.shouldDrop(meta.critical(), lodLevel, tracked.visual().getId())) {
                        droppedUpdates.incrementAndGet();
                        if (lodLevel != LodLevel.NEAR) {
                            lodSkippedMetadataUpdates.incrementAndGet();
                        }
                        continue;
                    }
                    long tokenVersion = tokenProvider.applyAsLong(tracked.visual().getId());
                    PendingMetadata finalMeta = meta;
                    scheduler.enqueueMetadataCoalesced(playerId, tracked.visual().getId(),
                            () -> packetBuffer.buffer(playerId, finalMeta.packet(), PacketPriority.METADATA, tracked.visual().getId(), false,
                                    finalMeta.critical(), -1L, tokenVersion));
                    tracked.clearPendingMetaPacket();
                }

                processedGroups++;
            }

            if (groupBudget != Integer.MAX_VALUE) {
                int remaining = Math.max(0, totalVisible - scanned);
                if (remaining > 0) {
                    transformChecksSkippedDueBudget.addAndGet(remaining);
                }
            }
        } finally {
            trackingState.markTransformTaskDone();
        }
    }
}
