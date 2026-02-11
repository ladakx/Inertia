package com.ladakx.inertia.rendering;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
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

    private final Map<Integer, TrackedVisual> visualsById = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerTrackingState> playerTrackingStates = new ConcurrentHashMap<>();
    private final Map<Long, Set<Integer>> chunkGrid = new ConcurrentHashMap<>();

    private final Map<UUID, PlayerPacketQueue> packetBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> pendingDestroyIds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingDestroyBacklogSinceNanos = new ConcurrentHashMap<>();

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

        LinkedHashSet<Integer> removedIds = new LinkedHashSet<>();

        for (Integer id : ids) {
            if (id == null) {
                continue;
            }

            TrackedVisual tracked = visualsById.remove(id);
            if (tracked == null) {
                continue;
            }

            removeFromGrid(id, tracked.location());
            removedIds.add(id);
        }

        if (removedIds.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, PlayerTrackingState> entry : playerTrackingStates.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerTrackingState trackingState = entry.getValue();
            Set<Integer> visible = trackingState.visibleIds();

            LinkedHashSet<Integer> hiddenForPlayer = null;
            for (Integer removedId : removedIds) {
                if (visible.remove(removedId)) {
                    trackingState.markVisibleIterationDirty();
                    if (hiddenForPlayer == null) {
                        hiddenForPlayer = new LinkedHashSet<>();
                    }
                    hiddenForPlayer.add(removedId);
                }
            }

            if (hiddenForPlayer != null && !hiddenForPlayer.isEmpty()) {
                Set<Integer> queue = pendingDestroyIds.computeIfAbsent(playerId, ignored -> new HashSet<>());
                if (queue.isEmpty()) {
                    pendingDestroyBacklogSinceNanos.put(playerId, System.nanoTime());
                }
                queue.addAll(hiddenForPlayer);
            }
        }
    }

    public void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
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
                        networkScheduler.enqueueSpawn(() ->
                                bufferPacket(playerId, tracked.visual().createSpawnPacket(spawnLoc, spawnRot), PacketPriority.SPAWN)
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
                boolean shouldSendUpdate = tracked.shouldSendUpdate(
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
                if (!shouldSendUpdate && lodLevel != LodLevel.NEAR && tracked.hasSignificantNearChange(posThresholdSq, rotThresholdDot)) {
                    lodSkippedUpdates.incrementAndGet();
                }

                Object updatePacket = shouldSendUpdate ? tracked.getPendingUpdatePacket() : null;
                if (updatePacket != null) {
                    bufferPacket(playerId, updatePacket, PacketPriority.TELEPORT, tracked.visual().getId(), true, false);
                    tracked.markSent(player);
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
                            networkScheduler.enqueueMetadataCoalesced(tracked.visual().getId(),
                                    () -> bufferPacket(playerId, meta.packet(), PacketPriority.METADATA, tracked.visual().getId(), false, meta.critical()));
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
        bufferPacket(playerId, packet, priority, null, false, false);
    }

    private void bufferPacket(UUID playerId, Object packet, PacketPriority priority, Integer visualId, boolean canCoalesce, boolean criticalMetadata) {
        if (packet == null) return;
        int estimatedBytes = Math.max(1, packetFactory.estimatePacketSizeBytes(packet));
        int coalesced = packetBuffer.computeIfAbsent(playerId, k -> new PlayerPacketQueue())
                .add(new QueuedPacket(packet, priority, estimatedBytes, visualId, canCoalesce, criticalMetadata, System.nanoTime()));
        if (coalesced > 0) {
            coalescedUpdates.addAndGet(coalesced);
        }
    }

    private void flushPackets() {
        if (packetFactory == null) return;

        long totalSent = 0;
        int onlinePlayersWithPackets = 0;
        int tickPeak = 0;

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
            int maxBytes = maxBytesPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxBytesPerPlayerPerTick;

            List<Object> packetsToSend = new ArrayList<>();
            int sentPackets = 0;
            int sentBytes = 0;

            while (sentPackets < maxPacketsWithDestroyBurst) {
                QueuedPacket next = queue.peek();
                if (next == null) break;

                boolean isDestroyPacket = next.priority() == PacketPriority.DESTROY;
                if (sentPackets >= maxPackets && !(destroyDrainFastPathActive && isDestroyPacket)) {
                    break;
                }

                boolean fitsByteBudget = sentBytes + next.estimatedBytes() <= maxBytes;
                if (!fitsByteBudget && sentPackets > 0) break;

                QueuedPacket polled = queue.poll();
                if (polled == null) break;

                packetsToSend.add(polled.packet());
                sentPackets++;
                sentBytes += polled.estimatedBytes();
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

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        pendingDestroyIds.remove(uuid);
        pendingDestroyBacklogSinceNanos.remove(uuid);
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
        pendingDestroyBacklogSinceNanos.clear();
        pendingDestroyIdsBacklog = 0;
        destroyQueueDepthBacklog = 0;
        destroyDrainFastPathActive = false;
        oldestQueueAgeMillis = 0L;
        destroyBacklogAgeMillis = 0L;
        packetBuffer.clear();
        networkScheduler.clear();
    }

    private void enqueuePendingDestroyTasks() {
        if (pendingDestroyIds.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Set<Integer>>> iterator = pendingDestroyIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<Integer>> entry = iterator.next();
            UUID playerId = entry.getKey();
            Set<Integer> ids = entry.getValue();
            iterator.remove();
            pendingDestroyBacklogSinceNanos.remove(playerId);

            if (ids == null || ids.isEmpty()) {
                continue;
            }

            int[] idArray = ids.stream().mapToInt(Integer::intValue).toArray();
            if (idArray.length == 0) {
                continue;
            }

            if (idArray.length == 1) {
                int targetId = idArray[0];
                networkScheduler.enqueueDestroy(() ->
                        bufferPacket(playerId, packetFactory.createDestroyPacket(targetId), PacketPriority.DESTROY)
                );
                continue;
            }

            networkScheduler.enqueueDestroy(() -> {
                try {
                    bufferPacket(playerId, packetFactory.createDestroyPacket(idArray), PacketPriority.DESTROY);
                } catch (Throwable ignored) {
                    for (int id : idArray) {
                        bufferPacket(playerId, packetFactory.createDestroyPacket(id), PacketPriority.DESTROY);
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
        for (Long sinceNanos : pendingDestroyBacklogSinceNanos.values()) {
            if (sinceNanos == null) {
                continue;
            }
            pendingAgeNanos = Math.max(pendingAgeNanos, Math.max(0L, now - sinceNanos));
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
        for (Set<Integer> pendingIds : pendingDestroyIds.values()) {
            if (pendingIds == null || pendingIds.isEmpty()) {
                continue;
            }
            total += pendingIds.size();
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
                                long enqueuedAtNanos) {}

    private static class PlayerPacketQueue {
        private final EnumMap<PacketPriority, ArrayDeque<QueuedPacket>> byPriority = new EnumMap<>(PacketPriority.class);
        private final Map<Integer, QueuedPacket> lastTeleportByVisualId = new HashMap<>();

        private PlayerPacketQueue() {
            byPriority.put(PacketPriority.DESTROY, new ArrayDeque<>());
            byPriority.put(PacketPriority.SPAWN, new ArrayDeque<>());
            byPriority.put(PacketPriority.TELEPORT, new ArrayDeque<>());
            byPriority.put(PacketPriority.METADATA, new ArrayDeque<>());
        }

        public int add(QueuedPacket packet) {
            int coalesced = 0;
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
    }

    private record PendingMetadata(Object packet, boolean critical) {}

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

        private Object cachedUpdatePacket = null;
        private PendingMetadata cachedMetaPacket = null;
        private boolean metaDirty = false;
        private boolean criticalMetaDirty = false;

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
            this.metaDirty = true;
            this.criticalMetaDirty = this.criticalMetaDirty || critical;
        }

        public void beginTick() {
            this.cachedUpdatePacket = null;
            this.cachedMetaPacket = null;

            if (this.metaDirty) {
                this.cachedMetaPacket = new PendingMetadata(visual.createMetadataPacket(), criticalMetaDirty);
                this.metaDirty = false;
                this.criticalMetaDirty = false;
            }
        }

        public boolean hasSignificantNearChange(float nearPosThresholdSq, float nearRotThresholdDot) {
            return isTransformChanged(nearSyncState, nearPosThresholdSq, nearRotThresholdDot);
        }

        public boolean shouldSendUpdate(
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
                case NEAR -> emitUpdateIfChanged(nearSyncState, nearPosThresholdSq, nearRotThresholdDot);
                case MID -> {
                    boolean changed = isTransformChanged(midSyncState, midPosThresholdSq, midRotThresholdDot);
                    if (!changed || !isIntervalReached(lastMidUpdateTick, tick, midIntervalTicks)) {
                        yield false;
                    }
                    boolean emitted = emitUpdate(midSyncState);
                    if (emitted) {
                        lastMidUpdateTick = tick;
                    }
                    yield emitted;
                }
                case FAR -> {
                    boolean changed = isTransformChanged(farSyncState, farPosThresholdSq, farRotThresholdDot);
                    if (!changed || !isIntervalReached(lastFarUpdateTick, tick, farIntervalTicks)) {
                        yield false;
                    }
                    boolean emitted = emitUpdate(farSyncState);
                    if (emitted) {
                        lastFarUpdateTick = tick;
                    }
                    yield emitted;
                }
            };
        }

        private boolean emitUpdateIfChanged(SyncState state, float posThresholdSq, float rotThresholdDot) {
            if (!isTransformChanged(state, posThresholdSq, rotThresholdDot)) {
                return false;
            }
            return emitUpdate(state);
        }

        private boolean emitUpdate(SyncState state) {
            if (this.cachedUpdatePacket == null) {
                this.cachedUpdatePacket = visual.createTeleportPacket(location, rotation);
            }
            sync(state);
            return true;
        }

        private boolean isTransformChanged(SyncState state, float posThresholdSq, float rotThresholdDot) {
            float dx = (float) location.getX() - state.pos.x;
            float dy = (float) location.getY() - state.pos.y;
            float dz = (float) location.getZ() - state.pos.z;
            float distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > posThresholdSq) {
                return true;
            }

            float dot = Math.abs(rotation.dot(state.rot));
            return dot < rotThresholdDot;
        }

        private boolean isIntervalReached(long lastTick, long currentTick, int intervalTicks) {
            if (lastTick == Long.MIN_VALUE) {
                return true;
            }
            return (currentTick - lastTick) >= intervalTicks;
        }

        public Object getPendingUpdatePacket() {
            return cachedUpdatePacket;
        }

        public PendingMetadata getPendingMetaPacket() {
            return cachedMetaPacket;
        }

        public void markSent(Player player) {
        }

        private void syncAll() {
            sync(nearSyncState);
            sync(midSyncState);
            sync(farSyncState);
        }

        private void sync(SyncState state) {
            state.pos.set((float) location.getX(), (float) location.getY(), (float) location.getZ());
            state.rot.set(rotation);
        }

        private static class SyncState {
            private final Vector3f pos = new Vector3f();
            private final Quaternionf rot = new Quaternionf();
        }
    }
}
