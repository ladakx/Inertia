package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChunkPhysicsManager implements AutoCloseable {

    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, CompletableFuture<GreedyMeshData>> inFlight = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> generationRevisions = new ConcurrentHashMap<>();
    private final Map<Long, PendingCaptureRequest> pendingCaptureRequests = new ConcurrentHashMap<>();
    private final GenerationQueue generationQueue;
    private final ChunkPhysicsCache cache;
    private final PhysicsGenerator<GreedyMeshData> generator;
    private final ExecutorService cacheIoExecutor;
    private final AtomicLong totalCaptureNanos = new AtomicLong();
    private final AtomicLong captureSamples = new AtomicLong();
    private final AtomicLong skippedByBudgetCount = new AtomicLong();

    public ChunkPhysicsManager(GenerationQueue generationQueue,
                               ChunkPhysicsCache cache,
                               PhysicsGenerator<GreedyMeshData> generator) {
        this.generationQueue = generationQueue;
        this.cache = cache;
        this.generator = generator;
        this.cacheIoExecutor = Executors.newSingleThreadExecutor(new CacheIoThreadFactory());
    }

    public void requestChunkGeneration(String worldName,
                                       int chunkX,
                                       int chunkZ,
                                       Supplier<ChunkSnapshotData> snapshotSupplier,
                                       Consumer<GreedyMeshData> onReady) {
        requestChunkGeneration(worldName, chunkX, chunkZ, snapshotSupplier, onReady, null);
    }

    public void requestChunkGeneration(String worldName,
                                       int chunkX,
                                       int chunkZ,
                                       Supplier<ChunkSnapshotData> snapshotSupplier,
                                       Consumer<GreedyMeshData> onReady,
                                       DirtyChunkRegion dirtyRegion) {
        GenerationRequestKind requestKind = dirtyRegion == null ? GenerationRequestKind.GENERATE_ON_LOAD : GenerationRequestKind.DIRTY;
        requestChunkGeneration(worldName, chunkX, chunkZ, snapshotSupplier, onReady, dirtyRegion, requestKind);
    }

    public void requestChunkGeneration(String worldName,
                                       int chunkX,
                                       int chunkZ,
                                       Supplier<ChunkSnapshotData> snapshotSupplier,
                                       Consumer<GreedyMeshData> onReady,
                                       DirtyChunkRegion dirtyRegion,
                                       GenerationRequestKind requestKind) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        if (!queuedChunks.add(key) || generator == null || generationQueue == null) {
            return;
        }
        if (dirtyRegion != null || cache == null) {
            enqueueCapture(worldName, chunkX, chunkZ, snapshotSupplier, onReady, key, dirtyRegion, requestKind);
            return;
        }

        int cacheRevision = generationRevisions.computeIfAbsent(key, unused -> new AtomicInteger()).get();

        CompletableFuture
                .supplyAsync(() -> cache.get(chunkX, chunkZ), cacheIoExecutor)
                .whenComplete((cached, throwable) -> {
                    if (!queuedChunks.contains(key)) {
                        return;
                    }
                    if (throwable != null) {
                        InertiaLogger.warn("Failed to read terrain cache at " + chunkX + ", " + chunkZ, throwable);
                    } else if (cached != null
                            && cached.isPresent()
                            && generationRevisions.computeIfAbsent(key, unused -> new AtomicInteger()).get() == cacheRevision) {
                        queuedChunks.remove(key);
                        onReady.accept(cached.get().meshData());
                        return;
                    }
                    enqueueCapture(worldName, chunkX, chunkZ, snapshotSupplier, onReady, key, null, requestKind);
                });
    }

    public void cancelChunk(int chunkX, int chunkZ) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        queuedChunks.remove(key);
        pendingCaptureRequests.remove(key);
        CompletableFuture<GreedyMeshData> future = inFlight.remove(key);
        if (future != null) {
            future.cancel(true);
        }
    }

    public void invalidate(int chunkX, int chunkZ) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        generationRevisions.computeIfAbsent(key, unused -> new AtomicInteger()).incrementAndGet();
        pendingCaptureRequests.remove(key);
        CompletableFuture<GreedyMeshData> future = inFlight.remove(key);
        if (future != null) {
            future.cancel(true);
        }
        if (cache != null) {
            cacheIoExecutor.execute(() -> cache.invalidate(chunkX, chunkZ));
        }
    }

    public void invalidateAll() {
        if (cache != null) {
            cacheIoExecutor.execute(cache::invalidateAll);
        }
    }

    public void processCaptureQueue(List<ChunkCoordinate> playerChunks, int maxCaptureMillisPerTick) {
        if (pendingCaptureRequests.isEmpty()) {
            return;
        }
        int budgetMillis = Math.max(0, maxCaptureMillisPerTick);
        long budgetNanos = budgetMillis <= 0 ? Long.MAX_VALUE : budgetMillis * 1_000_000L;
        long tickStarted = System.nanoTime();

        while (true) {
            if ((System.nanoTime() - tickStarted) >= budgetNanos) {
                int skipped = pendingCaptureRequests.size();
                if (skipped > 0) {
                    skippedByBudgetCount.addAndGet(skipped);
                }
                return;
            }
            PendingCaptureRequest request = pollHighestPriorityRequest(playerChunks);
            if (request == null) {
                return;
            }
            captureAndGenerate(request);
        }
    }

    public CaptureMetrics getCaptureMetrics() {
        long samples = captureSamples.get();
        double averageMillis = samples == 0 ? 0.0 : (totalCaptureNanos.get() / 1_000_000.0) / samples;
        return new CaptureMetrics(pendingCaptureRequests.size(), averageMillis, skippedByBudgetCount.get());
    }

    private void enqueueCapture(String worldName,
                                int chunkX,
                                int chunkZ,
                                Supplier<ChunkSnapshotData> snapshotSupplier,
                                Consumer<GreedyMeshData> onReady,
                                long key,
                                DirtyChunkRegion dirtyRegion,
                                GenerationRequestKind requestKind) {
        PendingCaptureRequest request = new PendingCaptureRequest(
                worldName,
                chunkX,
                chunkZ,
                key,
                snapshotSupplier,
                onReady,
                dirtyRegion,
                requestKind,
                System.nanoTime()
        );
        pendingCaptureRequests.put(key, request);
    }

    private PendingCaptureRequest pollHighestPriorityRequest(List<ChunkCoordinate> playerChunks) {
        long nowNanos = System.nanoTime();
        PendingCaptureRequest bestRequest = null;
        double bestScore = Double.MAX_VALUE;
        for (PendingCaptureRequest request : pendingCaptureRequests.values()) {
            double score = scoreRequest(request, playerChunks, nowNanos);
            if (score < bestScore) {
                bestScore = score;
                bestRequest = request;
            }
        }
        if (bestRequest == null) {
            return null;
        }
        return pendingCaptureRequests.remove(bestRequest.key(), bestRequest) ? bestRequest : null;
    }

    private double scoreRequest(PendingCaptureRequest request, List<ChunkCoordinate> playerChunks, long nowNanos) {
        double base = request.requestKind() == GenerationRequestKind.DIRTY ? 0.0 : 10_000.0;
        double distanceScore = distanceSqToNearestPlayerChunk(request.chunkX(), request.chunkZ(), playerChunks);
        double ageMillis = Math.max(0.0, (nowNanos - request.enqueueNanos()) / 1_000_000.0);
        return base + distanceScore - (ageMillis * 2.0);
    }

    private double distanceSqToNearestPlayerChunk(int chunkX, int chunkZ, List<ChunkCoordinate> playerChunks) {
        if (playerChunks == null || playerChunks.isEmpty()) {
            return 4096.0;
        }
        double nearest = Double.MAX_VALUE;
        for (ChunkCoordinate playerChunk : playerChunks) {
            double dx = chunkX - playerChunk.x();
            double dz = chunkZ - playerChunk.z();
            double distance = dx * dx + dz * dz;
            if (distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    private void captureAndGenerate(PendingCaptureRequest request) {
        if (!queuedChunks.contains(request.key())) {
            return;
        }
        ChunkSnapshotData snapshot;
        long started = System.nanoTime();
        try {
            snapshot = request.snapshotSupplier().get();
        } catch (Exception ex) {
            queuedChunks.remove(request.key());
            InertiaLogger.warn("Failed to capture chunk for " + request.worldName() + " at " + request.chunkX() + ", " + request.chunkZ(), ex);
            return;
        }
        long elapsed = System.nanoTime() - started;
        totalCaptureNanos.addAndGet(elapsed);
        captureSamples.incrementAndGet();

        if (snapshot == null) {
            queuedChunks.remove(request.key());
            return;
        }

        CompletableFuture<Optional<CachedChunkPhysicsData>> previousFuture = cache == null
                ? CompletableFuture.completedFuture(Optional.empty())
                : CompletableFuture.supplyAsync(() -> cache.get(request.chunkX(), request.chunkZ()), cacheIoExecutor)
                .exceptionally(ex -> {
                    InertiaLogger.warn("Failed to read terrain cache for merge at " + request.chunkX() + ", " + request.chunkZ(), ex);
                    return Optional.empty();
                });

        int generationId = generationRevisions.computeIfAbsent(request.key(), unused -> new AtomicInteger()).get();
        CompletableFuture<GreedyMeshData> future = generationQueue.submit(() -> generator.generate(snapshot, request.dirtyRegion()));
        inFlight.put(request.key(), future);
        future.whenComplete((generatedData, throwable) -> {
            inFlight.remove(request.key());
            queuedChunks.remove(request.key());
            if (throwable != null) {
                InertiaLogger.warn("Failed to generate physics chunk at " + request.chunkX() + ", " + request.chunkZ(), throwable);
                return;
            }
            if (generationRevisions.computeIfAbsent(request.key(), unused -> new AtomicInteger()).get() != generationId) {
                return;
            }

            previousFuture.whenComplete((previous, previousThrowable) -> {
                if (previousThrowable != null) {
                    InertiaLogger.warn("Failed to resolve terrain cache merge data at " + request.chunkX() + ", " + request.chunkZ(), previousThrowable);
                }
                CachedChunkPhysicsData previousData = previous == null ? null : previous.orElse(null);
                GreedyMeshData finalData;
                if (request.dirtyRegion() != null && previousData == null) {
                    // Dirty generation can be partial by design. If this is the first update and
                    // previous cache data is not available yet, force full rebuild to avoid
                    // replacing the chunk with only the changed region.
                    finalData = generator.generate(snapshot, null);
                } else {
                    finalData = mergeUnchangedSections(snapshot, generatedData, previousData, request.dirtyRegion());
                }
                if (cache != null) {
                    cacheIoExecutor.execute(() -> cache.put(
                            request.chunkX(),
                            request.chunkZ(),
                            new CachedChunkPhysicsData(finalData, snapshot.sectionFingerprints())
                    ));
                }
                request.onReady().accept(finalData);
            });
        });
    }

    private GreedyMeshData mergeUnchangedSections(ChunkSnapshotData snapshot,
                                                  GreedyMeshData generated,
                                                  CachedChunkPhysicsData previous,
                                                  DirtyChunkRegion dirtyRegion) {
        if (generated == null || snapshot == null || previous == null) {
            return generated;
        }

        Map<Integer, List<GreedyMeshShape>> mergedBySection = new HashMap<>(generated.sectionShapes());
        Map<Integer, List<GreedyMeshShape>> previousBySection = previous.meshData().sectionShapes();
        Set<Integer> touchedSections = generated.touchedSections();
        boolean dirtyUpdate = dirtyRegion != null;

        for (int sectionIndex = 0; sectionIndex < snapshot.sectionsCount(); sectionIndex++) {
            int sectionY = snapshot.minSectionY() + sectionIndex;
            if (dirtyUpdate && touchedSections.contains(sectionY)) {
                continue;
            }
            if (!previous.sectionFingerprintMatches(snapshot, sectionIndex)) {
                continue;
            }
            List<GreedyMeshShape> oldShapes = previousBySection.get(sectionY);
            if (oldShapes == null || oldShapes.isEmpty()) {
                mergedBySection.remove(sectionY);
            } else {
                mergedBySection.put(sectionY, oldShapes);
            }
        }

        List<GreedyMeshShape> mergedShapes = new ArrayList<>();
        for (List<GreedyMeshShape> sectionShapes : mergedBySection.values()) {
            mergedShapes.addAll(sectionShapes);
        }

        return new GreedyMeshData(mergedShapes, generated.fullRebuild(), generated.touchedSections());
    }

    @Override
    public void close() {
        inFlight.values().forEach(future -> future.cancel(true));
        inFlight.clear();
        queuedChunks.clear();
        pendingCaptureRequests.clear();
        if (generationQueue != null) {
            generationQueue.close();
        }
        cacheIoExecutor.shutdownNow();
    }

    public enum GenerationRequestKind {
        DIRTY,
        GENERATE_ON_LOAD
    }

    public record ChunkCoordinate(int x, int z) {
    }

    public record CaptureMetrics(int queueDepth, double averageCaptureMillis, long skippedByBudgetCount) {
    }

    private record PendingCaptureRequest(String worldName,
                                         int chunkX,
                                         int chunkZ,
                                         long key,
                                         Supplier<ChunkSnapshotData> snapshotSupplier,
                                         Consumer<GreedyMeshData> onReady,
                                         DirtyChunkRegion dirtyRegion,
                                         GenerationRequestKind requestKind,
                                         long enqueueNanos) {
    }

    private static class CacheIoThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("inertia-terrain-cache-io-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
