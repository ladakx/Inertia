package com.ladakx.inertia.rendering.tracker.processor;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.rendering.tracker.budget.RenderNetworkBudgetScheduler;
import com.ladakx.inertia.rendering.tracker.packet.PacketPriority;
import com.ladakx.inertia.rendering.tracker.state.PlayerFrame;
import com.ladakx.inertia.rendering.tracker.state.PlayerTrackingState;
import com.ladakx.inertia.rendering.tracker.state.TrackedVisual;
import org.bukkit.Location;
import org.bukkit.World;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Function;

public final class VisibilitySliceProcessor {

    @FunctionalInterface
    public interface PacketBuffer {
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
    private final TransformSliceProcessor.LodResolver lodResolver;
    private final PacketFactory packetFactory;
    private final IntFunction<int[]> passengersOfVehicle;
    private final TransformSliceProcessor.GroupMembersProvider groupMembersProvider;

    public VisibilitySliceProcessor(Map<Integer, TrackedVisual> visualsById,
                             RenderNetworkBudgetScheduler scheduler,
                             PacketBuffer packetBuffer,
                             java.util.function.IntToLongFunction tokenProvider,
                             Function<UUID, PlayerFrame> playerFrameProvider,
                             TransformSliceProcessor.LodResolver lodResolver,
                             PacketFactory packetFactory,
                             IntFunction<int[]> passengersOfVehicle,
                             TransformSliceProcessor.GroupMembersProvider groupMembersProvider) {
        this.visualsById = Objects.requireNonNull(visualsById, "visualsById");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packetBuffer = Objects.requireNonNull(packetBuffer, "packetBuffer");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
        this.playerFrameProvider = Objects.requireNonNull(playerFrameProvider, "playerFrameProvider");
        this.lodResolver = Objects.requireNonNull(lodResolver, "lodResolver");
        this.packetFactory = Objects.requireNonNull(packetFactory, "packetFactory");
        this.passengersOfVehicle = Objects.requireNonNull(passengersOfVehicle, "passengersOfVehicle");
        this.groupMembersProvider = Objects.requireNonNull(groupMembersProvider, "groupMembersProvider");
    }

    public void runSlice(UUID playerId,
                  PlayerTrackingState trackingState,
                  double viewDistanceSquared,
                  int maxVisibilityUpdatesPerPlayerPerTick,
                  long tickCounter,
                  boolean spawnRequirePhysicsSync,
                  int spawnDelayTicks,
                  int maxPendingSpawnsPerPlayerPerTick,
                  boolean destroyDrainFastPathActive,
                  Runnable rescheduleIfNeeded) {
        try {
            PlayerFrame playerFrame = playerFrameProvider.apply(playerId);
            if (playerFrame == null) {
                trackingState.clearVisible();
                return;
            }
            UUID playerWorldId = playerFrame.worldId();

            processPendingSpawns(
                    playerId,
                    playerFrame,
                    playerWorldId,
                    trackingState,
                    viewDistanceSquared,
                    tickCounter,
                    spawnRequirePhysicsSync,
                    maxPendingSpawnsPerPlayerPerTick
            );

            if (!trackingState.needsVisibilityPass()) {
                return;
            }

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
                    if (!tracked.isEnabled()) {
                        trackingState.clearPendingSpawn(id);
                        if (trackingState.removeVisible(id)) {
                            scheduler.enqueueDestroy(() -> {
                                packetBuffer.buffer(playerId, tracked.visual().createDestroyPacket(), PacketPriority.DESTROY,
                                        null, false, false, -1L, -1L);
                                enqueueMountRefreshForRemoved(playerId, trackingState, tracked, playerFrame);
                            });
                        }
                        processed++;
                        continue;
                    }
                    if (!tracked.isAllowedForProtocol(playerFrame.clientProtocol())) {
                        trackingState.clearPendingSpawn(id);
                        if (trackingState.removeVisible(id)) {
                            scheduler.enqueueDestroy(() -> {
                                packetBuffer.buffer(playerId, tracked.visual().createDestroyPacket(), PacketPriority.DESTROY,
                                        null, false, false, -1L, -1L);
                                enqueueMountRefreshForRemoved(playerId, trackingState, tracked, playerFrame);
                            });
                        }
                        processed++;
                        continue;
                    }
                    com.ladakx.inertia.rendering.tracker.state.LodLevel lod = lodResolver.resolve(distSq);
                    if (!tracked.isAllowedForLod(lod)) {
                        trackingState.clearPendingSpawn(id);
                        if (trackingState.removeVisible(id)) {
                            scheduler.enqueueDestroy(() -> {
                                packetBuffer.buffer(playerId, tracked.visual().createDestroyPacket(), PacketPriority.DESTROY,
                                        null, false, false, -1L, -1L);
                                enqueueMountRefreshForRemoved(playerId, trackingState, tracked, playerFrame);
                            });
                        }
                        processed++;
                        continue;
                    }
                    if (!trackingState.isVisible(id)) {
                        if (spawnRequirePhysicsSync && tracked.requiresPhysicsSync() && !tracked.isReadyToSpawn(tickCounter)) {
                            trackingState.scheduleSpawn(id, tickCounter + 1);
                            processed++;
                            continue;
                        }
                        Long dueTick = spawnRequirePhysicsSync ? tickCounter : trackingState.getPendingSpawnTick(id);
                        if (dueTick == null) {
                            if (spawnDelayTicks <= 0) {
                                dueTick = tickCounter;
                            } else {
                                trackingState.scheduleSpawn(id, tickCounter + spawnDelayTicks);
                            }
                        }
                        if (dueTick != null && dueTick <= tickCounter) {
                            trackingState.clearPendingSpawn(id);
                            spawnGroupTransactional(playerId, trackingState, tracked, playerFrame, lod);
                        }
                    }
                } else if (trackingState.removeVisible(id)) {
                    destroyGroupTransactional(playerId, trackingState, tracked);
                } else {
                    trackingState.clearPendingSpawn(id);
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

    private void processPendingSpawns(UUID playerId,
                                      PlayerFrame playerFrame,
                                      UUID playerWorldId,
                                      PlayerTrackingState trackingState,
                                      double viewDistanceSquared,
                                      long tickCounter,
                                      boolean spawnRequirePhysicsSync,
                                      int maxPendingSpawnsPerPlayerPerTick) {
        if (!trackingState.hasPendingSpawns()) {
            return;
        }
        int budget = maxPendingSpawnsPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxPendingSpawnsPerPlayerPerTick;
        int processed = 0;

        java.util.Iterator<java.util.Map.Entry<Integer, Long>> it = trackingState.pendingSpawnEntries().iterator();
        while (it.hasNext() && processed < budget) {
            java.util.Map.Entry<Integer, Long> entry = it.next();
            Integer id = entry.getKey();
            if (id == null) {
                it.remove();
                continue;
            }
            long dueTick = entry.getValue() != null ? entry.getValue() : Long.MIN_VALUE;
            if (dueTick > tickCounter) {
                continue;
            }

            TrackedVisual tracked = visualsById.get(id);
            if (tracked == null) {
                it.remove();
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

            if (!inRange || !tracked.isEnabled() || !tracked.isAllowedForProtocol(playerFrame.clientProtocol())) {
                it.remove();
                processed++;
                continue;
            }
            com.ladakx.inertia.rendering.tracker.state.LodLevel lod = lodResolver.resolve(distSq);
            if (!tracked.isAllowedForLod(lod)) {
                it.remove();
                processed++;
                continue;
            }

            if (spawnRequirePhysicsSync && tracked.requiresPhysicsSync() && !tracked.isReadyToSpawn(tickCounter)) {
                entry.setValue(tickCounter + 1);
                processed++;
                continue;
            }

            if (trackingState.isVisible(id)) {
                it.remove();
                processed++;
                continue;
            }

            it.remove();
            spawnGroupTransactional(playerId, trackingState, tracked, playerFrame, lod);

            processed++;
        }
    }

    private void spawnGroupTransactional(UUID playerId,
                                        PlayerTrackingState trackingState,
                                        TrackedVisual seed,
                                        PlayerFrame playerFrame,
                                        com.ladakx.inertia.rendering.tracker.state.LodLevel lodLevel) {
        if (seed == null || playerFrame == null) return;

        int groupKey = seed.groupKey();
        int[] members = groupMembersProvider.membersOf(groupKey);
        if (members == null || members.length == 0) {
            members = new int[]{seed.visual().getId()};
            groupKey = seed.visual().getId();
        }

        ArrayList<Object> packets = new ArrayList<>(members.length * 2);
        HashSet<Integer> vehiclesToRefresh = new HashSet<>();

        for (int memberId : members) {
            TrackedVisual tracked = visualsById.get(memberId);
            if (tracked == null) continue;

            // Only spawn entities not yet visible.
            if (trackingState.isVisible(memberId)) continue;

            if (!tracked.isEnabled()
                    || !tracked.isAllowedForProtocol(playerFrame.clientProtocol())
                    || !tracked.isAllowedForLod(lodLevel)) {
                trackingState.clearPendingSpawn(memberId);
                continue;
            }

            Location spawnLoc = tracked.location().clone();
            Quaternionf spawnRot = new Quaternionf(tracked.rotation());
            packets.add(tracked.visual().createSpawnPacket(spawnLoc, spawnRot));
            trackingState.addVisible(memberId);
            trackingState.clearPendingSpawn(memberId);

            if (tracked.isPassenger()) {
                vehiclesToRefresh.add(tracked.mountVehicleId());
            } else {
                int[] passengers = passengersOfVehicle.apply(memberId);
                if (passengers != null && passengers.length > 0) {
                    vehiclesToRefresh.add(memberId);
                }
            }
        }

        // Add mount packets after spawn packets (ensures client knows entities).
        for (int vehicleId : vehiclesToRefresh) {
            if (vehicleId < 0) continue;
            if (!trackingState.isVisible(vehicleId)) continue;

            TrackedVisual vehicle = visualsById.get(vehicleId);
            if (vehicle == null) continue;
            if (!vehicle.isAllowedForProtocol(playerFrame.clientProtocol())) continue;

            int[] allPassengers = passengersOfVehicle.apply(vehicleId);
            if (allPassengers == null) allPassengers = new int[0];

            int count = 0;
            for (int pid : allPassengers) {
                if (trackingState.isVisible(pid)) count++;
            }
            int[] visiblePassengers = new int[count];
            int j = 0;
            for (int pid : allPassengers) {
                if (!trackingState.isVisible(pid)) continue;
                visiblePassengers[j++] = pid;
            }

            Object mountPacket = packetFactory.createMountPacket(vehicleId, visiblePassengers);
            if (mountPacket != null) {
                packets.add(mountPacket);
            }
        }

        if (packets.isEmpty()) {
            return;
        }

        long tokenVersion = tokenProvider.applyAsLong(groupKey);
        int finalGroupKey = groupKey;
        ArrayList<Object> finalPackets = packets;
        scheduler.enqueueSpawn(() -> packetBuffer.buffer(playerId, finalPackets, PacketPriority.SPAWN,
                        finalGroupKey, false, false, -1L, tokenVersion),
                finalGroupKey,
                tokenVersion
        );
    }

    private void destroyGroupTransactional(UUID playerId,
                                          PlayerTrackingState trackingState,
                                          TrackedVisual seed) {
        if (seed == null) return;

        int groupKey = seed.groupKey();
        int[] members = groupMembersProvider.membersOf(groupKey);
        if (members == null || members.length == 0) {
            members = new int[]{seed.visual().getId()};
            groupKey = seed.visual().getId();
        }

        ArrayList<Object> packets = new ArrayList<>(members.length);
        for (int memberId : members) {
            TrackedVisual tracked = visualsById.get(memberId);
            if (tracked == null) continue;

            trackingState.clearPendingSpawn(memberId);
            if (!trackingState.isVisible(memberId)) continue;
            if (!trackingState.removeVisible(memberId)) continue;
            packets.add(tracked.visual().createDestroyPacket());
        }

        if (packets.isEmpty()) return;

        long tokenVersion = tokenProvider.applyAsLong(groupKey);
        int finalGroupKey = groupKey;
        ArrayList<Object> finalPackets = packets;
        scheduler.enqueueDestroy(() -> packetBuffer.buffer(playerId, finalPackets, PacketPriority.DESTROY,
                finalGroupKey, false, false, -1L, tokenVersion));
    }

    private void enqueueMountRefreshForRemoved(UUID playerId,
                                               PlayerTrackingState trackingState,
                                               TrackedVisual removed,
                                               PlayerFrame playerFrame) {
        if (removed == null || playerFrame == null) return;
        if (!removed.isPassenger()) return;
        enqueueMountRefresh(playerId, trackingState, removed.mountVehicleId(), playerFrame);
    }

    private void enqueueMountRefresh(UUID playerId,
                                     PlayerTrackingState trackingState,
                                     int vehicleId,
                                     PlayerFrame playerFrame) {
        if (vehicleId < 0) return;
        if (!trackingState.isVisible(vehicleId)) return;

        TrackedVisual vehicle = visualsById.get(vehicleId);
        if (vehicle == null) return;
        if (!vehicle.isAllowedForProtocol(playerFrame.clientProtocol())) return;

        int[] allPassengers = passengersOfVehicle.apply(vehicleId);
        if (allPassengers == null) allPassengers = new int[0];

        int count = 0;
        for (int pid : allPassengers) {
            if (trackingState.isVisible(pid)) count++;
        }

        int[] visiblePassengers = new int[count];
        int j = 0;
        for (int pid : allPassengers) {
            if (!trackingState.isVisible(pid)) continue;
            visiblePassengers[j++] = pid;
        }

        Object mountPacket = packetFactory.createMountPacket(vehicleId, visiblePassengers);
        if (mountPacket == null) return;

        long tokenVersion = tokenProvider.applyAsLong(vehicleId);
        packetBuffer.buffer(playerId, mountPacket, PacketPriority.METADATA,
                vehicleId, false, false, -1L, tokenVersion);
    }
}
