package com.ladakx.inertia.rendering;

import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkEntityTracker {

    private static final long DEFAULT_TOMBSTONE_TTL_TICKS = 3L;

    private final Map<Integer, TrackedVisual> visualsById = new ConcurrentHashMap<>();
    private final VisualTokenService tokenService = new VisualTokenService();
    private final VisualTombstoneService tombstoneService = new VisualTombstoneService();
    private final Map<UUID, PlayerTrackingState> playerTrackingStates = new ConcurrentHashMap<>();
    private final ChunkGridIndex chunkGrid = new ChunkGridIndex();

    private final Map<UUID, PlayerPacketQueue> packetBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerPacketQueue> packetBufferBack = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerPacketQueue> intentBufferFront = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerPacketQueue> intentBufferBack = new ConcurrentHashMap<>();
    private volatile Map<UUID, PlayerPacketQueue> activeIntentBuffer = intentBufferBack;
    private volatile Map<UUID, PlayerPacketQueue> lastCompletedIntentBuffer = intentBufferBack;
    private final Map<UUID, PendingDestroyState> pendingDestroyIds = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerFrame> playerFrames = new ConcurrentHashMap<>();

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
    private volatile long tickCounter = 0L;

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
    private final VisibilitySliceProcessor visibilitySliceProcessor;
    private final TransformSliceProcessor transformSliceProcessor;
    private final PacketFlushProcessor packetFlushProcessor = new PacketFlushProcessor();
    private final UnregisterBatchProcessor unregisterBatchProcessor;
    private final VisualRegistry visualRegistry;
    private final PlayerTickProcessor playerTickProcessor;
    private final DestroyBacklogProcessor destroyBacklogProcessor;
    private final SheddingPolicy sheddingPolicy = new SheddingPolicy();
    private final ExecutorService asyncPhaseExecutor;
    private volatile CompletableFuture<Void> asyncPhaseFuture;
    private volatile int asyncBacklogTicks = 0;
    private volatile int backlogPressureMultiplier = 1;

    public NetworkEntityTracker(PacketFactory packetFactory) {
        this.packetFactory = packetFactory;
        int workers = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        this.asyncPhaseExecutor = new ForkJoinPool(workers);
        this.visualRegistry = new VisualRegistry(visualsById, chunkGrid, tokenService, tombstoneService);
        this.visibilitySliceProcessor = new VisibilitySliceProcessor(
                visualsById,
                networkScheduler,
                this::bufferPacket,
                this::currentVisualToken,
                playerFrames::get
        );
        this.transformSliceProcessor = new TransformSliceProcessor(
                visualsById,
                networkScheduler,
                this::bufferPacket,
                this::currentVisualToken,
                playerFrames::get
        );
        this.playerTickProcessor = new PlayerTickProcessor(
                playerTrackingStates,
                chunkGrid,
                this::enqueueVisibilitySlice,
                this::enqueueTransformSlice
        );
        this.unregisterBatchProcessor = new UnregisterBatchProcessor(
                visualsById,
                chunkGrid,
                tombstoneService,
                networkScheduler,
                playerTrackingStates,
                pendingDestroyIds,
                this::invalidateVisualQueues,
                DEFAULT_TOMBSTONE_TTL_TICKS
        );
        this.destroyBacklogProcessor = new DestroyBacklogProcessor(
                pendingDestroyIds,
                networkScheduler,
                packetFactory,
                this::bufferPacket,
                this::preFlushBeforeBulkDestroy
        );
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
        visualRegistry.register(visual, location, rotation);
    }

    public void unregister(@NotNull NetworkVisual visual) {
        Objects.requireNonNull(visual, "visual");
        unregisterById(visual.getId());
    }

    public void unregisterById(int id) {
        unregisterBatch(Collections.singleton(id));
    }

    public void unregisterBatch(@NotNull Collection<Integer> ids) {
        UnregisterBatchProcessor.Result result = unregisterBatchProcessor.unregisterBatch(ids, tickCounter);
        if (result.bulkFastPathUsed()) {
            massDestroyBoostTicksRemaining = Math.max(massDestroyBoostTicksRemaining, 2);
        }
    }

    public void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        visualRegistry.updateState(visual, location, rotation, tickCounter);
    }

    public boolean isVisualClosed(int visualId) {
        return visualRegistry.isClosed(visualId, tickCounter);
    }

    public void updateMetadata(@NotNull NetworkVisual visual) {
        updateMetadata(visual, false);
    }

    public void updateMetadata(@NotNull NetworkVisual visual, boolean critical) {
        visualRegistry.markMetaDirty(visual, critical);
    }

    public void tick(@NotNull Collection<? extends Player> players, double viewDistanceSquared) {
        tickCounter++;
        pruneExpiredTombstones();

        visualRegistry.beginTick();

        destroyBacklogProcessor.enqueuePendingDestroyTasks();
        applyDestroyMetrics(destroyBacklogProcessor.refreshMetrics(destroyBacklogThreshold));
        sheddingState = sheddingPolicy.compute(
                networkScheduler,
                destroyBacklogThreshold,
                pendingDestroyIdsBacklog,
                destroyQueueDepthBacklog,
                destroyDrainFastPathActive
        );

        int viewDistanceChunks = (int) Math.ceil(Math.sqrt(viewDistanceSquared) / 16.0);

        Collection<PlayerFrame> playerSnapshot = capturePlayerFrames(players);

        flushCompletedAsyncIntents();

        if (asyncPhaseFuture == null || asyncPhaseFuture.isDone()) {
            asyncBacklogTicks = 0;
            activeIntentBuffer = activeIntentBuffer == intentBufferBack ? intentBufferFront : intentBufferBack;
            lastCompletedIntentBuffer = activeIntentBuffer;
            asyncPhaseFuture = CompletableFuture.runAsync(() -> runAsyncPhaseB(
                    playerSnapshot,
                    viewDistanceSquared,
                    viewDistanceChunks
            ), asyncPhaseExecutor);
        } else {
            asyncBacklogTicks++;
        }

        applyDestroyMetrics(destroyBacklogProcessor.refreshMetrics(destroyBacklogThreshold));

        flushPackets();
    }


    private Collection<PlayerFrame> capturePlayerFrames(Collection<? extends Player> players) {
        ArrayList<PlayerFrame> snapshot = new ArrayList<>(players.size());
        HashSet<UUID> alive = new HashSet<>();
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world == null) {
                continue;
            }
            UUID playerId = player.getUniqueId();
            PlayerFrame frame = new PlayerFrame(
                    playerId,
                    world.getUID(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getBlockX() >> 4,
                    location.getBlockZ() >> 4
            );
            snapshot.add(frame);
            alive.add(playerId);
            playerFrames.put(playerId, frame);
        }
        playerFrames.keySet().removeIf(uuid -> !alive.contains(uuid));
        playerTrackingStates.keySet().removeIf(uuid -> !alive.contains(uuid));
        return snapshot;
    }

    private void runAsyncPhaseB(Collection<PlayerFrame> players, double viewDistanceSquared, int viewDistanceChunks) {
        int backlogMultiplier = 1 + Math.min(3, Math.max(0, asyncBacklogTicks) / 2);
        backlogPressureMultiplier = backlogMultiplier;

        playerTickProcessor.processPlayers(
                players,
                viewDistanceSquared,
                viewDistanceChunks,
                tickCounter,
                fullRecalcIntervalTicks,
                destroyDrainFastPathActive
        );

        networkScheduler.runTick(players);

    }

    private void flushCompletedAsyncIntents() {
        CompletableFuture<Void> localFuture = asyncPhaseFuture;
        if (localFuture == null || !localFuture.isDone()) {
            return;
        }
        try {
            localFuture.join();
        } catch (CompletionException ignored) {
        }
        asyncPhaseFuture = null;

        Map<UUID, PlayerPacketQueue> completedIntentBuffer = lastCompletedIntentBuffer;

        packetBufferBack.clear();
        for (Map.Entry<UUID, PlayerPacketQueue> entry : completedIntentBuffer.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerPacketQueue intentQueue = entry.getValue();
            if (intentQueue == null || intentQueue.isEmpty()) {
                continue;
            }
            PlayerPacketQueue stagedQueue = packetBufferBack.computeIfAbsent(playerId, k -> new PlayerPacketQueue());
            QueuedPacket qp;
            while ((qp = intentQueue.poll()) != null) {
                stagedQueue.add(qp, this::isTokenCurrent);
            }
        }
        completedIntentBuffer.clear();

        for (Map.Entry<UUID, PlayerPacketQueue> entry : packetBufferBack.entrySet()) {
            PlayerPacketQueue incoming = entry.getValue();
            if (incoming == null || incoming.isEmpty()) {
                continue;
            }
            PlayerPacketQueue target = packetBuffer.computeIfAbsent(entry.getKey(), k -> new PlayerPacketQueue());
            QueuedPacket qp;
            while ((qp = incoming.poll()) != null) {
                target.add(qp, this::isTokenCurrent);
            }
        }
        packetBufferBack.clear();
    }

    private void addTombstone(int visualId) {
        tombstoneService.add(visualId, tickCounter + DEFAULT_TOMBSTONE_TTL_TICKS);
    }

    private void clearTombstone(int visualId) {
        tombstoneService.clear(visualId);
    }

    private boolean isVisualTombstoned(int visualId) {
        return tombstoneService.isTombstoned(visualId, tickCounter);
    }

    private void pruneExpiredTombstones() {
        tombstoneService.pruneExpired(tickCounter);
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
        visibilitySliceProcessor.runSlice(
                playerId,
                trackingState,
                viewDistanceSquared,
                Math.max(16, maxVisibilityUpdatesPerPlayerPerTick / Math.max(1, backlogPressureMultiplier)),
                destroyDrainFastPathActive,
                () -> enqueueVisibilitySlice(playerId, trackingState, viewDistanceSquared)
        );
    }

    private void runTransformSlice(UUID playerId, PlayerTrackingState trackingState, double viewDistanceSquared) {
        transformSliceProcessor.runSlice(
                playerId,
                trackingState,
                viewDistanceSquared,
                Math.max(16, maxTransformChecksPerPlayerPerTick / Math.max(1, backlogPressureMultiplier)),
                tickCounter,
                posThresholdSq,
                rotThresholdDot,
                midPosThresholdSq,
                midRotThresholdDot,
                Math.max(1, midUpdateIntervalTicks * Math.max(1, backlogPressureMultiplier)),
                farPosThresholdSq,
                farRotThresholdDot,
                Math.max(1, farUpdateIntervalTicks * Math.max(1, backlogPressureMultiplier)),
                farAllowMetadataUpdates,
                sheddingState,
                this::resolveLodLevel,
                this::shouldDropMetadata,
                droppedUpdates,
                lodSkippedUpdates,
                lodSkippedMetadataUpdates,
                transformChecksSkippedDueBudget
        );
    }

    private void applyDestroyMetrics(DestroyBacklogProcessor.Metrics metrics) {
        if (metrics == null) {
            return;
        }
        pendingDestroyIdsBacklog = metrics.pendingDestroyIdsBacklog();
        destroyQueueDepthBacklog = metrics.destroyQueueDepthBacklog();
        destroyDrainFastPathActive = metrics.destroyDrainFastPathActive();
        oldestQueueAgeMillis = metrics.oldestQueueAgeMillis();
        destroyBacklogAgeMillis = metrics.destroyBacklogAgeMillis();
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
        int coalesced = activeIntentBuffer.computeIfAbsent(playerId, k -> new PlayerPacketQueue())
                .add(new QueuedPacket(packet, priority, estimatedBytes, visualId, canCoalesce, criticalMetadata,
                        System.nanoTime(), destroyRegisteredAtTick, tokenVersion), this::isTokenCurrent);
        if (coalesced > 0) {
            coalescedUpdates.addAndGet(coalesced);
        }
    }

    private void flushPackets() {
        boolean massDestroyBudgetBoost = massDestroyBoostTicksRemaining > 0;
        PacketFlushProcessor.FlushStats stats = packetFlushProcessor.flush(
                packetBuffer,
                packetFactory,
                this::isTokenCurrent,
                maxPacketsPerPlayerPerTick,
                destroyDrainExtraPacketsPerPlayerPerTick,
                destroyDrainFastPathActive,
                massDestroyBudgetBoost,
                maxBytesPerPlayerPerTick,
                tickCounter,
                droppedPackets,
                deferredPackets,
                destroyLatencyTickTotal,
                destroyLatencySamples,
                destroyLatencyPeakTicks
        );

        if (stats.onlinePlayersWithPackets() > 0) {
            double tickAverage = stats.totalSent() / (double) stats.onlinePlayersWithPackets();
            long sampleIndex = averageSamples.getAndIncrement();
            averagePacketsPerPlayer = ((averagePacketsPerPlayer * sampleIndex) + tickAverage) / (sampleIndex + 1);
        }

        if (stats.tickPeak() > peakPacketsPerPlayer) {
            peakPacketsPerPlayer = stats.tickPeak();
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
        packetBufferBack.remove(uuid);
        intentBufferFront.remove(uuid);
        intentBufferBack.remove(uuid);
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
        playerFrames.clear();
        pendingDestroyIdsBacklog = 0;
        destroyQueueDepthBacklog = 0;
        destroyDrainFastPathActive = false;
        oldestQueueAgeMillis = 0L;
        destroyBacklogAgeMillis = 0L;
        massDestroyBoostTicksRemaining = 0;
        packetBuffer.clear();
        packetBufferBack.clear();
        intentBufferFront.clear();
        intentBufferBack.clear();
        tokenService.clear();
        tombstoneService.clear();
        networkScheduler.clear();
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

    private long bumpVisualToken(int visualId) {
        return tokenService.bump(visualId);
    }

    private long currentVisualToken(int visualId) {
        return tokenService.current(visualId);
    }

    private long invalidateVisualQueues(int visualId) {
        long activeToken = bumpVisualToken(visualId);
        for (Map<UUID, PlayerPacketQueue> buffer : List.of(packetBuffer, packetBufferBack, intentBufferFront, intentBufferBack)) {
            for (PlayerPacketQueue queue : buffer.values()) {
                if (queue == null) {
                    continue;
                }
                queue.invalidateVisual(visualId, activeToken);
            }
        }
        return activeToken;
    }

    private boolean isTokenCurrent(Integer visualId, long tokenVersion) {
        return tokenService.isCurrent(visualId, tokenVersion);
    }

    private void preFlushBeforeBulkDestroy(UUID playerId, int[] visualIds) {
        if (visualIds == null || visualIds.length == 0) {
            return;
        }
        for (Map<UUID, PlayerPacketQueue> buffer : List.of(packetBuffer, packetBufferBack, intentBufferFront, intentBufferBack)) {
            PlayerPacketQueue queue = buffer.get(playerId);
            if (queue != null) {
                queue.pruneBeforeBulkDestroy(visualIds);
            }
        }
        networkScheduler.invalidateVisuals(visualIds, this::currentVisualToken);
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

}
