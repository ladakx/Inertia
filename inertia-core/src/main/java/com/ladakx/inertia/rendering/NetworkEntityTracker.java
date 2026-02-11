package com.ladakx.inertia.rendering;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkEntityTracker {

    private static final long DEFAULT_TOMBSTONE_TTL_TICKS = 3L;

    private final Map<Integer, TrackedVisual> visualsById = new ConcurrentHashMap<>();
    private final Map<Integer, Long> visualTokenVersions = new ConcurrentHashMap<>();
    private final Map<Integer, Long> visualTombstones = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerTrackingState> playerTrackingStates = new ConcurrentHashMap<>();
    private final Map<Long, Set<Integer>> chunkGrid = new ConcurrentHashMap<>();

    private final Map<UUID, PlayerPacketQueue> packetBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, PendingDestroyState> pendingDestroyIds = new ConcurrentHashMap<>();

    // Default values matched with InertiaConfig defaults (0.01^2 and 0.3)
    private volatile float posThresholdSq = 0.0001f;
    private volatile float rotThresholdDot = 0.3f;
    private volatile float midPosThresholdSq = 0.0016f;
    private volatile float farPosThresholdSq = 0.0081f;
    private volatile float midRotThresholdDot = 0.2f;
    private volatile float farRotThresholdDot = 0.1f;
    private volatile float midDistanceSq = 576.0f;
    private volatile float farDistanceSq = 3136.0f;
    private volatile int midUpdateIntervalTicks = 2;
    private volatile int farUpdateIntervalTicks = 4;
    private volatile boolean farAllowMetadataUpdates = false;
    private volatile int maxVisibilityUpdatesPerPlayerPerTick = 256;
    private volatile int maxTransformChecksPerPlayerPerTick = 256;
    private volatile int fullRecalcIntervalTicks = 20;
    private volatile int maxPacketsPerPlayerPerTick = 256;
    private volatile int destroyBacklogThreshold = 512;
    private volatile int destroyDrainExtraPacketsPerPlayerPerTick = 128;
    private volatile int maxBytesPerPlayerPerTick = 98304;
    private long tickCounter = 0L;

    private volatile double averagePacketsPerPlayer = 0.0;
    private volatile int peakPacketsPerPlayer = 0;
    private final AtomicLong averageSamples = new AtomicLong(0);
    private final AtomicLong deferredPackets = new AtomicLong(0);
    private final AtomicLong droppedPackets = new AtomicLong(0);
    private final AtomicLong droppedUpdates = new AtomicLong(0);
    private final AtomicLong coalescedUpdates = new AtomicLong(0);
    private final AtomicLong lodSkippedUpdates = new AtomicLong(0);
    private final AtomicLong lodSkippedMetadataUpdates = new AtomicLong(0);
    private final AtomicLong transformChecksSkippedDueBudget = new AtomicLong(0);
    private volatile int pendingDestroyIdsBacklog = 0;
    private volatile int destroyQueueDepthBacklog = 0;
    private volatile boolean destroyDrainFastPathActive = false;
    private volatile long oldestQueueAgeMillis = 0L;
    private volatile long destroyBacklogAgeMillis = 0L;
    private volatile int massDestroyBoostTicksRemaining = 0;
    private final AtomicLong destroyLatencyTickTotal = new AtomicLong(0);
    private final AtomicLong destroyLatencySamples = new AtomicLong(0);
    private final AtomicLong destroyLatencyPeakTicks = new AtomicLong(0);
    private volatile SheddingState sheddingState = SheddingState.disabled();

    private final PacketFactory packetFactory;
    private final RenderNetworkBudgetScheduler networkScheduler = new RenderNetworkBudgetScheduler();

    public NetworkEntityTracker(PacketFactory packetFactory) {
        this.packetFactory = packetFactory;
    }

    public NetworkEntityTracker(PacketFactory packetFactory, InertiaConfig.RenderingSettings.NetworkEntityTrackerSettings settings) {
        this(packetFactory);
        applySettings(settings);
    }

    public void applySettings(InertiaConfig.RenderingSettings.NetworkEntityTrackerSettings settings) {
        if (settings == null) return;
        this.posThresholdSq = settings.posThresholdSq;
        this.rotThresholdDot = settings.rotThresholdDot;
        this.midPosThresholdSq = settings.midPosThresholdSq;
        this.farPosThresholdSq = settings.farPosThresholdSq;
        this.midRotThresholdDot = settings.midRotThresholdDot;
        this.farRotThresholdDot = settings.farRotThresholdDot;
        this.midDistanceSq = settings.midDistanceSq;
        this.farDistanceSq = settings.farDistanceSq;
        this.midUpdateIntervalTicks = settings.midUpdateIntervalTicks;
        this.farUpdateIntervalTicks = settings.farUpdateIntervalTicks;
        this.farAllowMetadataUpdates = settings.farAllowMetadataUpdates;
        this.maxVisibilityUpdatesPerPlayerPerTick = settings.maxVisibilityUpdatesPerPlayerPerTick;
        this.maxTransformChecksPerPlayerPerTick = settings.maxTransformChecksPerPlayerPerTick;
        this.fullRecalcIntervalTicks = settings.fullRecalcIntervalTicks;
        this.maxPacketsPerPlayerPerTick = settings.maxPacketsPerPlayerPerTick;
        this.destroyBacklogThreshold = settings.destroyBacklogThreshold;
        this.destroyDrainExtraPacketsPerPlayerPerTick = settings.destroyDrainExtraPacketsPerPlayerPerTick;
        this.maxBytesPerPlayerPerTick = settings.maxBytesPerPlayerPerTick;
        this.networkScheduler.applySettings(settings);
    }

    public record VisualRegistration(NetworkVisual visual, Location location, Quaternionf rotation) {
        public VisualRegistration {
            Objects.requireNonNull(visual, "visual");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(rotation, "rotation");
        }
    }

    public void registerBatch(@NotNull Collection<VisualRegistration> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        for (VisualRegistration registration : registrations) {
            if (registration == null) {
                continue;
            }
            register(registration.visual(), registration.location(), registration.rotation());
        }
    }

    public void register(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");

        clearTombstone(visual.getId());
        bumpVisualToken(visual.getId());

        TrackedVisual tracked = new TrackedVisual(visual, location.clone(), new Quaternionf(rotation));
        visualsById.put(visual.getId(), tracked);
        addToGrid(visual.getId(), location);
    }

    public void unregister(@NotNull NetworkVisual visual) {
        Objects.requireNonNull(visual, "visual");
        unregisterById(visual.getId());
    }

    public void unregisterById(int id) {
        unregisterBatch(Collections.singleton(id));
    }

    public void unregisterBatch(@NotNull Collection<Integer> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            return;
        }

        IntArrayList removedIds = new IntArrayList(ids.size());

        for (Integer id : ids) {
            if (id == null) {
                continue;
            }

            long activeTokenVersion = invalidateVisualQueues(id.intValue());
            networkScheduler.invalidateVisual(id.intValue(), activeTokenVersion);

            TrackedVisual tracked = visualsById.remove(id);
            if (tracked == null) {
                continue;
            }

            removeFromGrid(id, tracked.location());
            addTombstone(id);
            removedIds.add(id);
        }

        if (removedIds.isEmpty()) {
            return;
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

        if (useBulkFastPath) {
            massDestroyBoostTicksRemaining = Math.max(massDestroyBoostTicksRemaining, 2);
        }
    }

    public void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        if (isVisualTombstoned(visual.getId())) {
            return;
        }

        TrackedVisual tracked = visualsById.get(visual.getId());
        if (tracked != null) {
            long oldChunkKey = ChunkUtils.getChunkKey(tracked.location().getBlockX() >> 4, tracked.location().getBlockZ() >> 4);
            long newChunkKey = ChunkUtils.getChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);

            tracked.update(location, rotation);

            if (oldChunkKey != newChunkKey) {
                removeFromGrid(visual.getId(), oldChunkKey);
                addToGrid(visual.getId(), newChunkKey);
            }
        } else {
            register(visual, location, rotation);
        }
    }

    public boolean isVisualClosed(int visualId) {
        return isVisualTombstoned(visualId);
    }

    public void updateMetadata(@NotNull NetworkVisual visual) {
        updateMetadata(visual, false);
    }

    public void updateMetadata(@NotNull NetworkVisual visual, boolean critical) {
        TrackedVisual tracked = visualsById.get(visual.getId());
        if (tracked != null) {
            tracked.markMetaDirty(critical);
        }
    }

    public void tick(@NotNull Collection<? extends Player> players, double viewDistanceSquared) {
        tickCounter++;
        pruneExpiredTombstones();

        for (TrackedVisual tracked : visualsById.values()) {
            tracked.beginTick();
        }

        enqueuePendingDestroyTasks();
        refreshDestroyBacklogMetrics();
        sheddingState = computeSheddingState();

        int viewDistanceChunks = (int) Math.ceil(Math.sqrt(viewDistanceSquared) / 16.0);

        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;
            UUID playerId = player.getUniqueId();
            PlayerTrackingState trackingState = playerTrackingStates.computeIfAbsent(playerId, k -> new PlayerTrackingState());

            Location playerLoc = player.getLocation();
            int pChunkX = playerLoc.getBlockX() >> 4;
            int pChunkZ = playerLoc.getBlockZ() >> 4;
            World playerWorld = playerLoc.getWorld();
            if (playerWorld == null) continue;

            UUID playerWorldId = playerWorld.getUID();
            boolean chunkChanged = !trackingState.initialized() || pChunkX != trackingState.chunkX() || pChunkZ != trackingState.chunkZ();
            boolean worldChanged = !trackingState.initialized() || !playerWorldId.equals(trackingState.worldId());
            boolean periodicFullRecalc = !trackingState.initialized()
                    || (tickCounter - trackingState.lastFullRecalcTick()) >= fullRecalcIntervalTicks;
            boolean requiresFullRecalc = chunkChanged || worldChanged || periodicFullRecalc;

            trackingState.updatePosition(pChunkX, pChunkZ, playerWorldId);

            if (requiresFullRecalc) {
                trackingState.rebuildCandidates(chunkGrid, pChunkX, pChunkZ, viewDistanceChunks);
                trackingState.markFullRecalcDone(tickCounter);
                trackingState.markVisibilityDirty();
            }

            if (!destroyDrainFastPathActive && trackingState.needsVisibilityPass()) {
                enqueueVisibilitySlice(playerId, trackingState, viewDistanceSquared);
            }

            enqueueTransformSlice(playerId, trackingState, viewDistanceSquared);
        }

        playerTrackingStates.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);

        networkScheduler.runTick(players);

        refreshDestroyBacklogMetrics();

        flushPackets();
    }

    private void addTombstone(int visualId) {
        visualTombstones.put(visualId, tickCounter + DEFAULT_TOMBSTONE_TTL_TICKS);
    }

    private void clearTombstone(int visualId) {
        visualTombstones.remove(visualId);
    }

    private boolean isVisualTombstoned(int visualId) {
        Long expiresAtTick = visualTombstones.get(visualId);
        if (expiresAtTick == null) {
            return false;
        }
        if (tickCounter > expiresAtTick) {
            visualTombstones.remove(visualId, expiresAtTick);
            return false;
        }
        return true;
    }

    private void pruneExpiredTombstones() {
        visualTombstones.entrySet().removeIf(entry -> tickCounter > entry.getValue());
    }

    private void enqueueVisibilitySlice(UUID playerId, PlayerTrackingState trackingState, double viewDistanceSquared) {
        if (!trackingState.tryMarkVisibilityTaskQueued()) {
            return;
        }
        networkScheduler.enqueueVisibility(() -> runVisibilitySlice(playerId, trackingState, viewDistanceSquared));
    }

    private void enqueueTransformSlice(UUID playerId, PlayerTrackingState trackingState, double viewDistanceSquared) {
        if (!trackingState.tryMarkTransformTaskQueued()) {
            return;
        }
        networkScheduler.enqueueMetadata(() -> runTransformSlice(playerId, trackingState, viewDistanceSquared));
    }

    private void runVisibilitySlice(UUID playerId, PlayerTrackingState trackingState, double viewDistanceSquared) {
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
                        long tokenVersion = currentVisualToken(id);
                        networkScheduler.enqueueSpawn(() ->
                                bufferPacket(playerId, tracked.visual().createSpawnPacket(spawnLoc, spawnRot), PacketPriority.SPAWN,
                                        tracked.visual().getId(), false, false, -1L, tokenVersion)
                        , tracked.visual().getId(), tokenVersion
                        );
                        tracked.markSent(player);
                    }
                } else if (trackingState.removeVisible(id)) {
                    networkScheduler.enqueueDestroy(() -> bufferPacket(playerId, tracked.visual().createDestroyPacket(), PacketPriority.DESTROY));
                }

                processed++;
            }
        } finally {
            trackingState.markVisibilityTaskDone();
            if (!destroyDrainFastPathActive && trackingState.needsVisibilityPass()) {
                enqueueVisibilitySlice(playerId, trackingState, viewDistanceSquared);
            }
        }
    }

    private void runTransformSlice(UUID playerId, PlayerTrackingState trackingState, double viewDistanceSquared) {
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
                        networkScheduler.enqueueDestroy(() -> bufferPacket(playerId, tracked.visual().createDestroyPacket(), PacketPriority.DESTROY));
                    }
                    checked++;
                    continue;
                }

                LodLevel lodLevel = resolveLodLevel(distSq);
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
                if (positionPacket != null) {
                    long tokenVersion = currentVisualToken(tracked.visual().getId());
                    bufferPacket(playerId, positionPacket, PacketPriority.TELEPORT, tracked.visual().getId(), true, false, -1L, tokenVersion);
                    tracked.markSent(player);
                }

                Object transformMetaPacket = updateDecision.transformMetadataSent() ? tracked.getPendingTransformMetaPacket() : null;
                if (transformMetaPacket != null) {
                    long tokenVersion = currentVisualToken(tracked.visual().getId());
                    networkScheduler.enqueueMetadataCoalesced(tracked.visual().getId(),
                            () -> bufferPacket(playerId, transformMetaPacket, PacketPriority.METADATA, tracked.visual().getId(), false,
                                    updateDecision.transformMetadataForced(), -1L, tokenVersion));
                }

                PendingMetadata meta = tracked.getPendingMetaPacket();
                if (meta != null) {
                    boolean allowMetaPacket = lodLevel != LodLevel.FAR || farAllowMetadataUpdates;
                    if (allowMetaPacket) {
                        if (shouldDropMetadata(meta.critical(), lodLevel, tracked.visual().getId())) {
                            droppedUpdates.incrementAndGet();
                            if (lodLevel != LodLevel.NEAR) {
                                lodSkippedMetadataUpdates.incrementAndGet();
                            }
                        } else {
                            long tokenVersion = currentVisualToken(tracked.visual().getId());
                            networkScheduler.enqueueMetadataCoalesced(tracked.visual().getId(),
                                    () -> bufferPacket(playerId, meta.packet(), PacketPriority.METADATA, tracked.visual().getId(), false,
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

    private void bufferPacket(UUID playerId, Object packet, PacketPriority priority) {
        bufferPacket(playerId, packet, priority, null, false, false, -1L, -1L);
    }

    private void bufferPacket(UUID playerId, Object packet, PacketPriority priority, long destroyRegisteredAtTick) {
        bufferPacket(playerId, packet, priority, null, false, false, destroyRegisteredAtTick, -1L);
    }

    private void bufferPacket(UUID playerId, Object packet, PacketPriority priority, Integer visualId, boolean canCoalesce, boolean criticalMetadata) {
        bufferPacket(playerId, packet, priority, visualId, canCoalesce, criticalMetadata, -1L, -1L);
    }

    private void bufferPacket(UUID playerId, Object packet, PacketPriority priority, Integer visualId, boolean canCoalesce, boolean criticalMetadata, long destroyRegisteredAtTick) {
        bufferPacket(playerId, packet, priority, visualId, canCoalesce, criticalMetadata, destroyRegisteredAtTick, -1L);
    }

    private void bufferPacket(UUID playerId,
                              Object packet,
                              PacketPriority priority,
                              Integer visualId,
                              boolean canCoalesce,
                              boolean criticalMetadata,
                              long destroyRegisteredAtTick,
                              long tokenVersion) {
        if (packet == null) return;
        if (!isTokenCurrent(visualId, tokenVersion)) {
            droppedPackets.incrementAndGet();
            return;
        }
        int estimatedBytes = Math.max(1, packetFactory.estimatePacketSizeBytes(packet));
        int coalesced = packetBuffer.computeIfAbsent(playerId, k -> new PlayerPacketQueue())
                .add(new QueuedPacket(packet, priority, estimatedBytes, visualId, canCoalesce, criticalMetadata,
                        System.nanoTime(), destroyRegisteredAtTick, tokenVersion), this::isTokenCurrent);
        if (coalesced > 0) {
            coalescedUpdates.addAndGet(coalesced);
        }
    }

    private void flushPackets() {
        if (packetFactory == null) return;

        long totalSent = 0;
        int onlinePlayersWithPackets = 0;
        int tickPeak = 0;

        boolean massDestroyBudgetBoost = massDestroyBoostTicksRemaining > 0;

        for (Map.Entry<UUID, PlayerPacketQueue> entry : packetBuffer.entrySet()) {
            PlayerPacketQueue queue = entry.getValue();
            if (queue.isEmpty()) continue;

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                droppedPackets.addAndGet(queue.size());
                queue.clear();
                continue;
            }

            int maxPackets = maxPacketsPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxPacketsPerPlayerPerTick;
            int maxPacketsWithDestroyBurst = maxPackets;
            if (destroyDrainFastPathActive && destroyDrainExtraPacketsPerPlayerPerTick > 0 && maxPackets != Integer.MAX_VALUE) {
                maxPacketsWithDestroyBurst = maxPackets + destroyDrainExtraPacketsPerPlayerPerTick;
            }
            if (massDestroyBudgetBoost && maxPacketsWithDestroyBurst != Integer.MAX_VALUE) {
                maxPacketsWithDestroyBurst += Math.max(16, maxPacketsWithDestroyBurst / 2);
            }
            int maxBytes = maxBytesPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxBytesPerPlayerPerTick;
            int boostedDestroyBytes = massDestroyBudgetBoost && maxBytes != Integer.MAX_VALUE
                    ? maxBytes + (maxBytes / 2)
                    : maxBytes;

            List<Object> packetsToSend = new ArrayList<>();
            int sentPackets = 0;
            int sentBytes = 0;

            while (sentPackets < maxPacketsWithDestroyBurst) {
                QueuedPacket next = queue.peek();
                if (next == null) break;
                if (!isTokenCurrent(next.visualId(), next.tokenVersion())) {
                    queue.poll();
                    droppedPackets.incrementAndGet();
                    continue;
                }

                boolean isDestroyPacket = next.priority() == PacketPriority.DESTROY;
                boolean allowDestroyPacketBurst = isDestroyPacket && (destroyDrainFastPathActive || massDestroyBudgetBoost);
                if (sentPackets >= maxPackets && !allowDestroyPacketBurst) {
                    break;
                }

                int activeByteBudget = (massDestroyBudgetBoost && isDestroyPacket) ? boostedDestroyBytes : maxBytes;
                boolean fitsByteBudget = sentBytes + next.estimatedBytes() <= activeByteBudget;
                if (!fitsByteBudget && sentPackets > 0) break;

                QueuedPacket polled = queue.poll();
                if (polled == null) break;
                if (!isTokenCurrent(polled.visualId(), polled.tokenVersion())) {
                    droppedPackets.incrementAndGet();
                    continue;
                }

                packetsToSend.add(polled.packet());
                sentPackets++;
                sentBytes += polled.estimatedBytes();
                if (polled.priority() == PacketPriority.DESTROY && polled.destroyRegisteredAtTick() >= 0) {
                    long latencyTicks = Math.max(0L, tickCounter - polled.destroyRegisteredAtTick());
                    destroyLatencyTickTotal.addAndGet(latencyTicks);
                    destroyLatencySamples.incrementAndGet();
                    destroyLatencyPeakTicks.accumulateAndGet(latencyTicks, Math::max);
                }
            }

            if (!packetsToSend.isEmpty()) {
                packetFactory.sendBundle(player, packetsToSend);
                totalSent += sentPackets;
                onlinePlayersWithPackets++;
                if (sentPackets > tickPeak) {
                    tickPeak = sentPackets;
                }
            }

            int deferred = queue.size();
            if (deferred > 0) {
                deferredPackets.addAndGet(deferred);
            }
        }

        if (onlinePlayersWithPackets > 0) {
            double tickAverage = totalSent / (double) onlinePlayersWithPackets;
            long sampleIndex = averageSamples.getAndIncrement();
            averagePacketsPerPlayer = ((averagePacketsPerPlayer * sampleIndex) + tickAverage) / (sampleIndex + 1);
        }

        if (tickPeak > peakPacketsPerPlayer) {
            peakPacketsPerPlayer = tickPeak;
        }

        if (massDestroyBoostTicksRemaining > 0) {
            massDestroyBoostTicksRemaining--;
        }
    }

    public double getAveragePacketsPerPlayer() {
        return averagePacketsPerPlayer;
    }

    public int getPeakPacketsPerPlayer() {
        return peakPacketsPerPlayer;
    }

    public long getDeferredPackets() {
        return deferredPackets.get();
    }

    public long getDroppedPackets() {
        return droppedPackets.get();
    }

    public long getDroppedUpdates() {
        return droppedUpdates.get();
    }

    public long getCoalescedUpdates() {
        return coalescedUpdates.get() + networkScheduler.getCoalescedTaskCount();
    }

    public long getLodSkippedUpdates() {
        return lodSkippedUpdates.get();
    }

    public long getLodSkippedMetadataUpdates() {
        return lodSkippedMetadataUpdates.get();
    }

    public long getTransformChecksSkippedDueBudget() {
        return transformChecksSkippedDueBudget.get();
    }

    public int getSpawnQueueDepth() {
        return networkScheduler.getSpawnQueueDepth();
    }

    public int getVisibilityQueueDepth() {
        return networkScheduler.getVisibilityQueueDepth();
    }

    public int getMetadataQueueDepth() {
        return networkScheduler.getMetadataQueueDepth();
    }

    public int getDestroyQueueDepth() {
        return networkScheduler.getDestroyQueueDepth();
    }

    public int getPendingDestroyIdsBacklog() {
        return pendingDestroyIdsBacklog;
    }

    public int getDestroyQueueDepthBacklog() {
        return destroyQueueDepthBacklog;
    }

    public boolean isDestroyDrainFastPathActive() {
        return destroyDrainFastPathActive;
    }

    public long getSchedulerDeferredCount() {
        return networkScheduler.getLastTickDeferredTasks();
    }

    public long getSchedulerTickWorkNanos() {
        return networkScheduler.getLastTickWorkNanos();
    }

    public double getSchedulerSecondaryScale() {
        return networkScheduler.getLastSecondaryScale();
    }

    public long getOldestQueueAgeMillis() {
        return oldestQueueAgeMillis;
    }

    public long getDestroyBacklogAgeMillis() {
        return destroyBacklogAgeMillis;
    }

    public double getAverageDestroyLatencyTicks() {
        long samples = destroyLatencySamples.get();
        return samples <= 0 ? 0.0 : destroyLatencyTickTotal.get() / (double) samples;
    }

    public long getPeakDestroyLatencyTicks() {
        return destroyLatencyPeakTicks.get();
    }

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        pendingDestroyIds.remove(uuid);
        packetBuffer.remove(uuid);
        PlayerTrackingState trackingState = playerTrackingStates.remove(uuid);
        Set<Integer> visible = trackingState != null ? trackingState.visibleIds() : null;
        if (visible != null) {
            for (Integer id : visible) {
                TrackedVisual tracked = visualsById.get(id);
                if (tracked != null) {
                    packetFactory.sendPacket(player, tracked.visual().createDestroyPacket());
                }
            }
        }
    }

    public void clear() {
        visualsById.clear();
        playerTrackingStates.clear();
        chunkGrid.clear();
        pendingDestroyIds.clear();
        pendingDestroyIdsBacklog = 0;
        destroyQueueDepthBacklog = 0;
        destroyDrainFastPathActive = false;
        oldestQueueAgeMillis = 0L;
        destroyBacklogAgeMillis = 0L;
        massDestroyBoostTicksRemaining = 0;
        packetBuffer.clear();
        visualTokenVersions.clear();
        networkScheduler.clear();
    }

    private void enqueuePendingDestroyTasks() {
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
                networkScheduler.enqueueDestroy(() ->
                        bufferPacket(playerId, packetFactory.createDestroyPacket(targetId), PacketPriority.DESTROY, registeredAtTick)
                );
                continue;
            }

            networkScheduler.enqueueDestroy(() -> {
                preFlushBeforeBulkDestroy(playerId, idArray);
                try {
                    bufferPacket(playerId, packetFactory.createDestroyPacket(idArray), PacketPriority.DESTROY, registeredAtTick);
                } catch (Throwable ignored) {
                    for (int id : idArray) {
                        bufferPacket(playerId, packetFactory.createDestroyPacket(id), PacketPriority.DESTROY, registeredAtTick);
                    }
                }
            });
        }
    }

    private void refreshDestroyBacklogMetrics() {
        long now = System.nanoTime();
        int pendingBacklog = calculatePendingDestroyBacklog();
        int queueBacklog = networkScheduler.getDestroyQueueDepth();
        pendingDestroyIdsBacklog = pendingBacklog;
        destroyQueueDepthBacklog = queueBacklog;
        oldestQueueAgeMillis = networkScheduler.getOldestQueueAgeMillis();

        long pendingAgeNanos = 0L;
        for (PendingDestroyState state : pendingDestroyIds.values()) {
            if (state == null || state.backlogSinceNanos() <= 0L) {
                continue;
            }
            pendingAgeNanos = Math.max(pendingAgeNanos, Math.max(0L, now - state.backlogSinceNanos()));
        }
        long destroyQueueAgeNanos = networkScheduler.getDestroyQueueOldestAgeMillis() * 1_000_000L;
        destroyBacklogAgeMillis = Math.max(pendingAgeNanos, destroyQueueAgeNanos) / 1_000_000L;

        int threshold = Math.max(1, destroyBacklogThreshold);
        destroyDrainFastPathActive = pendingBacklog >= threshold || queueBacklog >= threshold;
    }

    private SheddingState computeSheddingState() {
        int metadataDepth = networkScheduler.getMetadataQueueDepth();
        int totalDepth = networkScheduler.queueDepth();
        int destroyDepth = networkScheduler.getDestroyQueueDepth() + pendingDestroyIdsBacklog;
        int threshold = Math.max(1, destroyBacklogThreshold);

        int intensity = 0;
        if (totalDepth > threshold) intensity++;
        if (metadataDepth > threshold / 2) intensity++;
        if (destroyDepth > threshold) intensity++;
        if (destroyDrainFastPathActive) intensity += 2;

        intensity = Math.min(4, intensity);
        return SheddingState.of(intensity);
    }

    private boolean shouldDropMetadata(boolean critical, LodLevel lodLevel, int visualId) {
        if (critical || lodLevel == LodLevel.NEAR) {
            return false;
        }
        int aggressiveness = sheddingState.metadataDropModulo();
        if (aggressiveness <= 1) {
            return false;
        }
        return Math.floorMod(Objects.hash(visualId, tickCounter), aggressiveness) != 0;
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

    private enum PacketPriority {
        DESTROY,
        SPAWN,
        TELEPORT,
        METADATA
    }

    private enum LodLevel {
        NEAR,
        MID,
        FAR
    }

    private record QueuedPacket(Object packet,
                                PacketPriority priority,
                                int estimatedBytes,
                                Integer visualId,
                                boolean coalescible,
                                boolean criticalMetadata,
                                long enqueuedAtNanos,
                                long destroyRegisteredAtTick,
                                long tokenVersion) {}

    private long bumpVisualToken(int visualId) {
        return visualTokenVersions.merge(visualId, 1L, Long::sum);
    }

    private long currentVisualToken(int visualId) {
        return visualTokenVersions.getOrDefault(visualId, 0L);
    }

    private long invalidateVisualQueues(int visualId) {
        long activeToken = bumpVisualToken(visualId);
        for (PlayerPacketQueue queue : packetBuffer.values()) {
            if (queue == null) {
                continue;
            }
            queue.invalidateVisual(visualId, activeToken);
        }
        return activeToken;
    }

    private boolean isTokenCurrent(Integer visualId, long tokenVersion) {
        if (visualId == null || tokenVersion < 0L) {
            return true;
        }
        return currentVisualToken(visualId.intValue()) == tokenVersion;
    }

    private void preFlushBeforeBulkDestroy(UUID playerId, int[] visualIds) {
        if (visualIds == null || visualIds.length == 0) {
            return;
        }
        PlayerPacketQueue queue = packetBuffer.get(playerId);
        if (queue != null) {
            queue.pruneBeforeBulkDestroy(visualIds);
        }
        networkScheduler.invalidateVisuals(visualIds, this::currentVisualToken);
    }

    private static final class PendingDestroyState {
        private final IntOpenHashSet ids = new IntOpenHashSet();
        private long backlogSinceNanos;
        private long firstUnregisterTick = -1L;

        private IntOpenHashSet ids() {
            return ids;
        }

        private long backlogSinceNanos() {
            return backlogSinceNanos;
        }

        private long firstUnregisterTick() {
            return firstUnregisterTick;
        }

        private void markBacklogStart(long nowNanos, long tick) {
            this.backlogSinceNanos = nowNanos;
            if (this.firstUnregisterTick < 0L) {
                this.firstUnregisterTick = tick;
            }
        }

        private void addAll(IntArrayList values) {
            for (int i = 0; i < values.size(); i++) {
                ids.add(values.getInt(i));
            }
        }
    }

    private static class PlayerPacketQueue {
        private final EnumMap<PacketPriority, ArrayDeque<QueuedPacket>> byPriority = new EnumMap<>(PacketPriority.class);
        private final Map<Integer, QueuedPacket> lastTeleportByVisualId = new HashMap<>();

        private PlayerPacketQueue() {
            byPriority.put(PacketPriority.DESTROY, new ArrayDeque<>());
            byPriority.put(PacketPriority.SPAWN, new ArrayDeque<>());
            byPriority.put(PacketPriority.TELEPORT, new ArrayDeque<>());
            byPriority.put(PacketPriority.METADATA, new ArrayDeque<>());
        }

        public int add(QueuedPacket packet, java.util.function.BiPredicate<Integer, Long> tokenValidator) {
            int coalesced = 0;
            if (packet == null) {
                return 0;
            }
            if (packet.visualId() != null && packet.tokenVersion() >= 0L && tokenValidator != null
                    && !tokenValidator.test(packet.visualId(), packet.tokenVersion())) {
                return 0;
            }
            if (packet.priority() == PacketPriority.TELEPORT && packet.coalescible() && packet.visualId() != null) {
                QueuedPacket previous = lastTeleportByVisualId.put(packet.visualId(), packet);
                if (previous != null && byPriority.get(PacketPriority.TELEPORT).remove(previous)) {
                    coalesced = 1;
                }
            }
            byPriority.get(packet.priority()).addLast(packet);
            return coalesced;
        }

        public QueuedPacket peek() {
            QueuedPacket packet = byPriority.get(PacketPriority.DESTROY).peekFirst();
            if (packet != null) return packet;
            packet = byPriority.get(PacketPriority.SPAWN).peekFirst();
            if (packet != null) return packet;
            packet = byPriority.get(PacketPriority.TELEPORT).peekFirst();
            if (packet != null) return packet;
            return byPriority.get(PacketPriority.METADATA).peekFirst();
        }

        public QueuedPacket poll() {
            QueuedPacket packet = byPriority.get(PacketPriority.DESTROY).pollFirst();
            if (packet != null) return packet;
            packet = byPriority.get(PacketPriority.SPAWN).pollFirst();
            if (packet != null) return packet;
            packet = byPriority.get(PacketPriority.TELEPORT).pollFirst();
            if (packet != null) {
                if (packet.visualId() != null) {
                    lastTeleportByVisualId.remove(packet.visualId(), packet);
                }
                return packet;
            }
            return byPriority.get(PacketPriority.METADATA).pollFirst();
        }

        public int size() {
            int total = 0;
            for (ArrayDeque<QueuedPacket> queue : byPriority.values()) {
                total += queue.size();
            }
            return total;
        }

        public boolean isEmpty() {
            return size() == 0;
        }

        public void clear() {
            byPriority.values().forEach(ArrayDeque::clear);
            lastTeleportByVisualId.clear();
        }

        public void invalidateVisual(int visualId, long activeTokenVersion) {
            pruneQueue(PacketPriority.SPAWN, visualId, activeTokenVersion);
            pruneQueue(PacketPriority.TELEPORT, visualId, activeTokenVersion);
            pruneQueue(PacketPriority.METADATA, visualId, activeTokenVersion);
        }

        public void pruneBeforeBulkDestroy(int[] visualIds) {
            if (visualIds == null || visualIds.length == 0) {
                return;
            }
            IntOpenHashSet set = new IntOpenHashSet(visualIds.length);
            for (int visualId : visualIds) {
                set.add(visualId);
            }
            pruneQueue(PacketPriority.SPAWN, set);
            pruneQueue(PacketPriority.TELEPORT, set);
            pruneQueue(PacketPriority.METADATA, set);
        }

        private void pruneQueue(PacketPriority priority, int visualId, long activeTokenVersion) {
            ArrayDeque<QueuedPacket> queue = byPriority.get(priority);
            if (queue == null || queue.isEmpty()) {
                return;
            }
            Iterator<QueuedPacket> iterator = queue.iterator();
            while (iterator.hasNext()) {
                QueuedPacket packet = iterator.next();
                if (packet.visualId() == null || packet.visualId() != visualId) {
                    continue;
                }
                if (packet.tokenVersion() >= 0L && packet.tokenVersion() != activeTokenVersion) {
                    iterator.remove();
                    if (priority == PacketPriority.TELEPORT) {
                        lastTeleportByVisualId.remove(visualId, packet);
                    }
                }
            }
        }

        private void pruneQueue(PacketPriority priority, IntOpenHashSet visualIds) {
            ArrayDeque<QueuedPacket> queue = byPriority.get(priority);
            if (queue == null || queue.isEmpty()) {
                return;
            }
            Iterator<QueuedPacket> iterator = queue.iterator();
            while (iterator.hasNext()) {
                QueuedPacket packet = iterator.next();
                Integer visualId = packet.visualId();
                if (visualId == null || !visualIds.contains(visualId.intValue())) {
                    continue;
                }
                iterator.remove();
                if (priority == PacketPriority.TELEPORT) {
                    lastTeleportByVisualId.remove(visualId, packet);
                }
            }
        }
    }

    private record PendingMetadata(Object packet, boolean critical) {}

    private record UpdateDecision(boolean positionSent, boolean transformMetadataSent, boolean transformMetadataForced) {
        private static final UpdateDecision NONE = new UpdateDecision(false, false, false);
    }

    private record SheddingState(int midTeleportIntervalMultiplier,
                                 int farTeleportIntervalMultiplier,
                                 int metadataDropModulo) {
        private static SheddingState disabled() {
            return new SheddingState(1, 1, 1);
        }

        private static SheddingState of(int intensity) {
            return switch (intensity) {
                case 0 -> disabled();
                case 1 -> new SheddingState(2, 2, 2);
                case 2 -> new SheddingState(2, 3, 3);
                case 3 -> new SheddingState(3, 4, 4);
                default -> new SheddingState(4, 6, 6);
            };
        }
    }

    private static class PlayerTrackingState {
        private int chunkX;
        private int chunkZ;
        private UUID worldId;
        private long lastFullRecalcTick;
        private boolean initialized;

        private final Set<Integer> visibleIds = new HashSet<>();
        private final List<Integer> visibleIterationOrder = new ArrayList<>();
        private final List<Integer> precomputedCandidateIds = new ArrayList<>();
        private final Set<Integer> candidateLookup = new HashSet<>();
        private int candidateCursor = 0;
        private int visibleCursor = 0;
        private boolean visibilityDirty;
        private boolean visibleIterationDirty;
        private boolean visibilityTaskQueued;
        private boolean transformTaskQueued;

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public UUID worldId() { return worldId; }
        public long lastFullRecalcTick() { return lastFullRecalcTick; }
        public boolean initialized() { return initialized; }
        public Set<Integer> visibleIds() { return visibleIds; }

        public void updatePosition(int chunkX, int chunkZ, UUID worldId) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.worldId = worldId;
            this.initialized = true;
        }

        public void rebuildCandidates(Map<Long, Set<Integer>> chunkGrid, int centerChunkX, int centerChunkZ, int viewDistanceChunks) {
            candidateLookup.clear();
            precomputedCandidateIds.clear();
            for (int x = -viewDistanceChunks; x <= viewDistanceChunks; x++) {
                for (int z = -viewDistanceChunks; z <= viewDistanceChunks; z++) {
                    long chunkKey = ChunkUtils.getChunkKey(centerChunkX + x, centerChunkZ + z);
                    Set<Integer> ids = chunkGrid.get(chunkKey);
                    if (ids != null && !ids.isEmpty()) {
                        for (Integer id : ids) {
                            if (candidateLookup.add(id)) {
                                precomputedCandidateIds.add(id);
                            }
                        }
                    }
                }
            }

            candidateCursor = 0;
            if (visibleIds.removeIf(id -> !candidateLookup.contains(id))) {
                visibleIterationDirty = true;
            }
            resetVisibleCursor();
        }

        public void markFullRecalcDone(long tick) {
            this.lastFullRecalcTick = tick;
        }

        public void markVisibilityDirty() {
            this.visibilityDirty = true;
            this.candidateCursor = 0;
        }

        public boolean needsVisibilityPass() {
            return visibilityDirty;
        }

        public void markVisibilityPassComplete() {
            this.visibilityDirty = false;
        }

        public Integer nextCandidateId() {
            if (candidateCursor >= precomputedCandidateIds.size()) {
                return null;
            }
            return precomputedCandidateIds.get(candidateCursor++);
        }

        public Integer nextVisibleId() {
            if (visibleIterationDirty) {
                visibleIterationOrder.clear();
                visibleIterationOrder.addAll(visibleIds);
                visibleIterationDirty = false;
            }

            int size = visibleIterationOrder.size();
            if (size == 0) {
                return null;
            }

            if (visibleCursor >= size) {
                visibleCursor = 0;
            }

            return visibleIterationOrder.get(visibleCursor++);
        }

        public void resetVisibleCursor() {
            this.visibleCursor = 0;
        }

        public int remainingVisibleChecks() {
            return Math.max(0, visibleIterationOrder.size() - visibleCursor);
        }

        public boolean addVisible(int id) {
            boolean added = visibleIds.add(id);
            if (added) {
                visibleIterationDirty = true;
            }
            return added;
        }

        public boolean removeVisible(int id) {
            boolean removed = visibleIds.remove(id);
            if (removed) {
                visibleIterationDirty = true;
            }
            return removed;
        }

        public void clearVisible() {
            if (!visibleIds.isEmpty()) {
                visibleIds.clear();
                visibleIterationDirty = true;
            }
            resetVisibleCursor();
        }

        public void markVisibleIterationDirty() {
            this.visibleIterationDirty = true;
        }

        public boolean tryMarkVisibilityTaskQueued() {
            if (visibilityTaskQueued) {
                return false;
            }
            visibilityTaskQueued = true;
            return true;
        }

        public void markVisibilityTaskDone() {
            visibilityTaskQueued = false;
        }

        public boolean tryMarkTransformTaskQueued() {
            if (transformTaskQueued) {
                return false;
            }
            transformTaskQueued = true;
            return true;
        }

        public void markTransformTaskDone() {
            transformTaskQueued = false;
        }
    }

    private void addToGrid(int id, Location loc) {
        long key = ChunkUtils.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        addToGrid(id, key);
    }

    private void addToGrid(int id, long chunkKey) {
        chunkGrid.computeIfAbsent(chunkKey, k -> new HashSet<>()).add(id);
    }

    private void removeFromGrid(int id, Location loc) {
        long key = ChunkUtils.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        removeFromGrid(id, key);
    }

    private void removeFromGrid(int id, long chunkKey) {
        Set<Integer> set = chunkGrid.get(chunkKey);
        if (set != null) {
            set.remove(id);
            if (set.isEmpty()) {
                chunkGrid.remove(chunkKey);
            }
        }
    }

    private LodLevel resolveLodLevel(double distanceSq) {
        if (distanceSq <= midDistanceSq) {
            return LodLevel.NEAR;
        }
        if (distanceSq <= farDistanceSq) {
            return LodLevel.MID;
        }
        return LodLevel.FAR;
    }

    private static class TrackedVisual {
        private final NetworkVisual visual;
        private final Location location;
        private final Quaternionf rotation;

        private final SyncState nearSyncState = new SyncState();
        private final SyncState midSyncState = new SyncState();
        private final SyncState farSyncState = new SyncState();

        private long lastMidUpdateTick = Long.MIN_VALUE;
        private long lastFarUpdateTick = Long.MIN_VALUE;

        private Object cachedPositionPacket = null;
        private Object cachedTransformMetaPacket = null;
        private PendingMetadata cachedMetaPacket = null;
        private boolean metadataDirty = false;
        private boolean criticalMetaDirty = false;
        private boolean forceTransformResyncDirty = false;

        private TrackedVisual(NetworkVisual visual, Location location, Quaternionf rotation) {
            this.visual = visual;
            this.location = location;
            this.rotation = rotation;
            syncAll();
        }

        public NetworkVisual visual() { return visual; }
        public Location location() { return location; }
        public Quaternionf rotation() { return rotation; }

        public void update(Location newLoc, Quaternionf newRot) {
            this.location.setWorld(newLoc.getWorld());
            this.location.setX(newLoc.getX());
            this.location.setY(newLoc.getY());
            this.location.setZ(newLoc.getZ());
            this.location.setYaw(newLoc.getYaw());
            this.location.setPitch(newLoc.getPitch());
            this.rotation.set(newRot);
        }

        public void markMetaDirty(boolean critical) {
            this.metadataDirty = true;
            this.criticalMetaDirty = this.criticalMetaDirty || critical;
            if (critical) {
                this.forceTransformResyncDirty = true;
            }
        }

        public void beginTick() {
            this.cachedPositionPacket = null;
            this.cachedTransformMetaPacket = null;
            this.cachedMetaPacket = null;

            if (this.metadataDirty) {
                this.cachedMetaPacket = new PendingMetadata(visual.createMetadataPacket(), criticalMetaDirty);
                this.metadataDirty = false;
                this.criticalMetaDirty = false;
            }
        }

        public boolean hasSignificantNearChange(float nearPosThresholdSq, float nearRotThresholdDot) {
            return isPositionChanged(nearSyncState, nearPosThresholdSq) || isRotationChanged(nearSyncState, nearRotThresholdDot);
        }

        public UpdateDecision prepareUpdate(
                LodLevel lodLevel,
                long tick,
                float nearPosThresholdSq,
                float nearRotThresholdDot,
                float midPosThresholdSq,
                float midRotThresholdDot,
                int midIntervalTicks,
                float farPosThresholdSq,
                float farRotThresholdDot,
                int farIntervalTicks
        ) {
            return switch (lodLevel) {
                case NEAR -> prepareNearUpdate(nearPosThresholdSq, nearRotThresholdDot);
                case MID -> prepareLodUpdate(midSyncState, tick, midPosThresholdSq, midRotThresholdDot, midIntervalTicks, true);
                case FAR -> prepareLodUpdate(farSyncState, tick, farPosThresholdSq, farRotThresholdDot, farIntervalTicks, false);
            };
        }

        private UpdateDecision prepareNearUpdate(float posThresholdSq, float rotThresholdDot) {
            boolean positionChanged = isPositionChanged(nearSyncState, posThresholdSq);
            boolean transformChanged = isRotationChanged(nearSyncState, rotThresholdDot);
            boolean forcedTransform = forceTransformResyncDirty;

            if (!positionChanged && !transformChanged && !forcedTransform) {
                return UpdateDecision.NONE;
            }

            if (positionChanged) {
                emitPosition(nearSyncState);
            }
            if (transformChanged || forcedTransform) {
                emitTransformMetadata(nearSyncState);
                forceTransformResyncDirty = false;
            }

            return new UpdateDecision(positionChanged, transformChanged || forcedTransform, forcedTransform);
        }

        private UpdateDecision prepareLodUpdate(SyncState state,
                                                long tick,
                                                float posThresholdSq,
                                                float rotThresholdDot,
                                                int intervalTicks,
                                                boolean midLod) {
            boolean positionChanged = isPositionChanged(state, posThresholdSq);
            boolean transformChanged = isRotationChanged(state, rotThresholdDot);
            boolean forcedTransform = forceTransformResyncDirty;
            boolean intervalReached = isIntervalReached(midLod ? lastMidUpdateTick : lastFarUpdateTick, tick, intervalTicks);

            boolean sendPosition = positionChanged && intervalReached;
            boolean sendTransform = forcedTransform || transformChanged || intervalReached;

            if (!sendPosition && !sendTransform) {
                return UpdateDecision.NONE;
            }

            if (sendPosition) {
                emitPosition(state);
            }
            if (sendTransform) {
                emitTransformMetadata(state);
                forceTransformResyncDirty = false;
            }

            if (midLod) {
                lastMidUpdateTick = tick;
            } else {
                lastFarUpdateTick = tick;
            }

            return new UpdateDecision(sendPosition, sendTransform, forcedTransform);
        }

        private void emitPosition(SyncState state) {
            if (this.cachedPositionPacket == null) {
                this.cachedPositionPacket = visual.createPositionPacket(location, rotation);
            }
            syncPosition(state);
        }

        private void emitTransformMetadata(SyncState state) {
            if (this.cachedTransformMetaPacket == null) {
                this.cachedTransformMetaPacket = visual.createTransformMetadataPacket(rotation);
            }
            syncRotation(state);
        }

        private boolean isPositionChanged(SyncState state, float posThresholdSq) {
            float dx = (float) location.getX() - state.pos.x;
            float dy = (float) location.getY() - state.pos.y;
            float dz = (float) location.getZ() - state.pos.z;
            float distSq = dx * dx + dy * dy + dz * dz;
            return distSq > posThresholdSq;
        }

        private boolean isRotationChanged(SyncState state, float rotThresholdDot) {
            float dot = Math.abs(rotation.dot(state.rot));
            return dot < rotThresholdDot;
        }

        private boolean isIntervalReached(long lastTick, long currentTick, int intervalTicks) {
            if (lastTick == Long.MIN_VALUE) {
                return true;
            }
            return (currentTick - lastTick) >= intervalTicks;
        }

        public Object getPendingPositionPacket() {
            return cachedPositionPacket;
        }

        public Object getPendingTransformMetaPacket() {
            return cachedTransformMetaPacket;
        }

        public PendingMetadata getPendingMetaPacket() {
            return cachedMetaPacket;
        }

        public void markSent(Player player) {
        }

        private void syncAll() {
            syncPosition(nearSyncState);
            syncRotation(nearSyncState);
            syncPosition(midSyncState);
            syncRotation(midSyncState);
            syncPosition(farSyncState);
            syncRotation(farSyncState);
        }

        private void syncPosition(SyncState state) {
            state.pos.set((float) location.getX(), (float) location.getY(), (float) location.getZ());
        }

        private void syncRotation(SyncState state) {
            state.rot.set(rotation);
        }

        private static class SyncState {
            private final Vector3f pos = new Vector3f();
            private final Quaternionf rot = new Quaternionf();
        }
    }
}
