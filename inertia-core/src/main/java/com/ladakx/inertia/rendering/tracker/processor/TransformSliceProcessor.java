package com.ladakx.inertia.rendering.tracker.processor;

import com.ladakx.inertia.rendering.tracker.budget.RenderNetworkBudgetScheduler;
import com.ladakx.inertia.rendering.tracker.packet.PacketPriority;
import com.ladakx.inertia.rendering.tracker.state.LodLevel;
import com.ladakx.inertia.rendering.tracker.budget.SheddingState;
import com.ladakx.inertia.rendering.tracker.state.PendingMetadata;
import com.ladakx.inertia.rendering.tracker.state.UpdateDecision;
import com.ladakx.inertia.rendering.tracker.state.PlayerFrame;
import com.ladakx.inertia.rendering.tracker.state.PlayerTrackingState;
import com.ladakx.inertia.rendering.tracker.state.TrackedVisual;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private final Map<Integer, TrackedVisual> visualsById;
    private final RenderNetworkBudgetScheduler scheduler;
    private final VisibilitySliceProcessor.PacketBuffer packetBuffer;
    private final java.util.function.IntToLongFunction tokenProvider;
    private final Function<UUID, PlayerFrame> playerFrameProvider;

    private static final class GroupPacketBatch {
        private final ArrayList<Object> packets = new ArrayList<>(4);

        private void addPacket(Object packet) {
            if (packet == null) {
                return;
            }
            packets.add(packet);
        }

        private boolean isEmpty() {
            return packets.isEmpty();
        }

        private Object packetPayload() {
            return packets.size() == 1 ? packets.get(0) : new ArrayList<>(packets);
        }
    }

    public TransformSliceProcessor(Map<Integer, TrackedVisual> visualsById,
                            RenderNetworkBudgetScheduler scheduler,
                            VisibilitySliceProcessor.PacketBuffer packetBuffer,
                            java.util.function.IntToLongFunction tokenProvider,
                            Function<UUID, PlayerFrame> playerFrameProvider) {
        this.visualsById = Objects.requireNonNull(visualsById, "visualsById");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packetBuffer = Objects.requireNonNull(packetBuffer, "packetBuffer");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
        this.playerFrameProvider = Objects.requireNonNull(playerFrameProvider, "playerFrameProvider");
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
            int playerProtocol = playerFrame.clientProtocol();

            int checkBudget = maxTransformChecksPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxTransformChecksPerPlayerPerTick;
            int checked = 0;
            LinkedHashMap<Integer, GroupPacketBatch> groupedTeleportPackets = new LinkedHashMap<>();
            Map<Integer, GroupContext> groupContexts = new HashMap<>();

            java.util.function.IntUnaryOperator groupKeyProvider = visualId -> {
                TrackedVisual tracked = visualsById.get(visualId);
                return tracked != null ? tracked.groupKey() : visualId;
            };
            while (checked < checkBudget) {
                Integer id = trackingState.nextVisibleId(groupKeyProvider);
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

                // Process composite-model groups atomically (avoid splitting parts across ticks).
                int groupKey = tracked.groupKey();
                while (true) {
                    if (tracked == null) {
                        trackingState.removeVisible(id);
                        checked++;
                    } else {
                        TrackedVisual trackedForContext = tracked;
                        GroupContext groupContext = groupContexts.computeIfAbsent(groupKey, key -> computeGroupContext(
                                key,
                                trackedForContext,
                                playerWorldId,
                                playerFrame.x(),
                                playerFrame.y(),
                                playerFrame.z(),
                                viewDistanceSquared,
                                lodResolver
                        ));

                        boolean allowed = tracked.isAllowedForProtocol(playerProtocol);
                        if (!groupContext.inRange || !allowed) {
                            if (trackingState.removeVisible(id)) {
                                Object destroyPacket = tracked.visual().createDestroyPacket();
                                scheduler.enqueueDestroy(() -> packetBuffer.buffer(playerId, destroyPacket, PacketPriority.DESTROY,
                                        null, false, false, -1L, -1L));
                            }
                            checked++;
                        } else {
                            LodLevel lodLevel = groupContext.lodLevel;
                            if (!tracked.isEnabled() || !tracked.isAllowedForLod(lodLevel)) {
                                if (trackingState.removeVisible(id)) {
                                    Object destroyPacket = tracked.visual().createDestroyPacket();
                                    scheduler.enqueueDestroy(() -> packetBuffer.buffer(playerId, destroyPacket, PacketPriority.DESTROY,
                                            null, false, false, -1L, -1L));
                                }
                                checked++;
                            } else {
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
                                    GroupPacketBatch batch = groupedTeleportPackets.computeIfAbsent(groupKey, unused -> new GroupPacketBatch());
                                    batch.addPacket(packetToSend);
                                    tracked.markSent();
                                }

                                if (transformMetaPacket != null) {
                                    // Rotation-only updates (no teleport) must still be tightly synchronized for all parts of the same model.
                                    // If we send these as individual METADATA packets, packet budgets can split parts across ticks which
                                    // becomes very visible when rotate-translation is enabled.
                                    GroupPacketBatch batch = groupedTeleportPackets.computeIfAbsent(groupKey, unused -> new GroupPacketBatch());
                                    batch.addPacket(transformMetaPacket);
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
                                            int visualId = tracked.visual().getId();
                                            long tokenVersion = tokenProvider.applyAsLong(visualId);
                                            scheduler.enqueueMetadataCoalesced(playerId, visualId,
                                                    () -> packetBuffer.buffer(playerId, meta.packet(), PacketPriority.METADATA, visualId, false,
                                                            meta.critical(), -1L, tokenVersion));
                                        }
                                    } else {
                                        lodSkippedMetadataUpdates.incrementAndGet();
                                    }
                                }
                                checked++;
                            }
                        }
                    }

                    // Continue with the next id if it belongs to the same composite-model group.
                    Integer nextId = trackingState.peekVisibleId(groupKeyProvider);
                    if (nextId == null) {
                        trackingState.resetVisibleCursor();
                        break;
                    }
                    TrackedVisual nextTracked = visualsById.get(nextId);
                    int nextGroupKey = nextTracked != null ? nextTracked.groupKey() : nextId;
                    if (nextGroupKey != groupKey) {
                        break;
                    }
                    id = trackingState.nextVisibleId(groupKeyProvider);
                    if (id == null) {
                        trackingState.resetVisibleCursor();
                        break;
                    }
                    tracked = visualsById.get(id);
                }

                // Respect the budget between groups, but never split a group.
                if (checked >= checkBudget) {
                    break;
                }
            }

            for (Map.Entry<Integer, GroupPacketBatch> entry : groupedTeleportPackets.entrySet()) {
                int groupKey = entry.getKey();
                GroupPacketBatch batch = entry.getValue();
                if (batch == null || batch.isEmpty() || groupKey < 0) {
                    continue;
                }
                // Coalesce by groupKey (model), not by an individual visual id, to keep composite models visually coherent.
                // groupKey is stable (it is the first visual id of the model).
                long groupTokenVersion = tokenProvider.applyAsLong(groupKey);
                packetBuffer.buffer(playerId, batch.packetPayload(), PacketPriority.TELEPORT, groupKey, true,
                        false, -1L, groupTokenVersion);
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

    private static final class GroupContext {
        private final boolean inRange;
        private final LodLevel lodLevel;

        private GroupContext(boolean inRange, LodLevel lodLevel) {
            this.inRange = inRange;
            this.lodLevel = lodLevel;
        }
    }

    private GroupContext computeGroupContext(int groupKey,
                                             TrackedVisual fallback,
                                             UUID playerWorldId,
                                             double playerX,
                                             double playerY,
                                             double playerZ,
                                             double viewDistanceSquared,
                                             LodResolver lodResolver) {
        TrackedVisual representative = visualsById.get(groupKey);
        if (representative == null) {
            representative = fallback;
        }

        Location trackedLoc = representative.location();
        World trackedWorld = trackedLoc.getWorld();
        boolean sameWorld = trackedWorld != null && playerWorldId.equals(trackedWorld.getUID());

        double dx = trackedLoc.getX() - playerX;
        double dy = trackedLoc.getY() - playerY;
        double dz = trackedLoc.getZ() - playerZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        boolean inRange = sameWorld && distSq <= viewDistanceSquared;
        LodLevel lodLevel = lodResolver.resolve(distSq);
        return new GroupContext(inRange, lodLevel);
    }
}
