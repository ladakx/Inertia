package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;

import java.util.List;
import java.util.Map;
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

    private final Map<Long, RequestEntry> requests = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> generationRevisions = new ConcurrentHashMap<>();
    private final GenerationQueue generationQueue;
    private final ChunkPhysicsCache cache;
    private final PhysicsGenerator<GreedyMeshData> generator;
    private final ExecutorService cacheIoExecutor;
    private final AtomicLong totalCaptureNanos = new AtomicLong();
    private final AtomicLong captureSamples = new AtomicLong();
    private final AtomicLong totalGenerateNanos = new AtomicLong();
    private final AtomicLong generateSamples = new AtomicLong();
    private final AtomicLong totalApplyNanos = new AtomicLong();
    private final AtomicLong applySamples = new AtomicLong();
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
        if (generator == null || generationQueue == null) {
            return;
        }

        requests.compute(key, (unused, existing) -> {
            if (existing != null && existing.state() != RequestState.APPLIED) {
                return existing;
            }
            return new RequestEntry(
                    worldName,
                    chunkX,
                    chunkZ,
                    key,
                    snapshotSupplier,
                    onReady,
                    dirtyRegion,
                    requestKind,
                    RequestState.REQUESTED,
                    null,
                    System.nanoTime(),
                    null
            );
        });

        if (requestKind == GenerationRequestKind.DIRTY || dirtyRegion != null || cache == null) {
            return;
        }

        int cacheRevision = generationRevisions.computeIfAbsent(key, unused -> new AtomicInteger()).get();
        CompletableFuture
                .supplyAsync(() -> cache.get(chunkX, chunkZ), cacheIoExecutor)
                .whenComplete((cached, throwable) -> {
                    RequestEntry entry = requests.get(key);
                    if (entry == null || entry.state() != RequestState.REQUESTED) {
                        return;
                    }
                    if (throwable != null) {
                        InertiaLogger.warn("Failed to read terrain cache at " + chunkX + ", " + chunkZ, throwable);
                        return;
                    }
                    if (cached != null
                            && cached.isPresent()
                            && generationRevisions.computeIfAbsent(key, unused -> new AtomicInteger()).get() == cacheRevision) {
                        completeApply(entry, cached.get().meshData());
                    }
                });
    }

    public void cancelChunk(int chunkX, int chunkZ) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        RequestEntry request = requests.remove(key);
        if (request != null && request.future() != null) {
            request.future().cancel(true);
        }
    }

    public void invalidate(int chunkX, int chunkZ) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        generationRevisions.computeIfAbsent(key, unused -> new AtomicInteger()).incrementAndGet();
        RequestEntry request = requests.remove(key);
        if (request != null && request.future() != null) {
            request.future().cancel(true);
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

    public void processCaptureQueue(List<ChunkCoordinate> playerChunks, int maxCapturePerTick, int maxCaptureMillisPerTick) {
        if (requests.isEmpty()) {
            return;
        }
        int chunkBudget = Math.max(1, maxCapturePerTick);
        int budgetMillis = Math.max(0, maxCaptureMillisPerTick);
        long budgetNanos = budgetMillis <= 0 ? Long.MAX_VALUE : budgetMillis * 1_000_000L;
        long tickStarted = System.nanoTime();
        int captured = 0;

        while (captured < chunkBudget) {
            if ((System.nanoTime() - tickStarted) >= budgetNanos) {
                skippedByBudgetCount.addAndGet(countRequestsInState(RequestState.REQUESTED));
                return;
            }
            RequestEntry request = pollHighestPriorityRequest(playerChunks);
            if (request == null) {
                return;
            }
            captureAndGenerate(request);
            captured++;
        }
    }

    public CaptureMetrics getCaptureMetrics() {
        return new CaptureMetrics(
                countRequestsInState(RequestState.REQUESTED),
                averageMillis(totalCaptureNanos, captureSamples),
                averageMillis(totalGenerateNanos, generateSamples),
                averageMillis(totalApplyNanos, applySamples),
                skippedByBudgetCount.get(),
                generationQueue.getQueueDepth(),
                generationQueue.getInFlightJobs()
        );
    }

    private long countRequestsInState(RequestState state) {
        return requests.values().stream().filter(request -> request.state() == state).count();
    }

    private double averageMillis(AtomicLong totalNanos, AtomicLong sampleCount) {
        long samples = sampleCount.get();
        return samples == 0 ? 0.0 : (totalNanos.get() / 1_000_000.0) / samples;
    }

    private RequestEntry pollHighestPriorityRequest(List<ChunkCoordinate> playerChunks) {
        long nowNanos = System.nanoTime();
        RequestEntry bestRequest = null;
        double bestScore = Double.MAX_VALUE;
        for (RequestEntry request : requests.values()) {
            if (request.state() != RequestState.REQUESTED) {
                continue;
            }
            double score = scoreRequest(request, playerChunks, nowNanos);
            if (score < bestScore) {
                bestScore = score;
                bestRequest = request;
            }
        }
        if (bestRequest == null) {
            return null;
        }
        RequestEntry captured = bestRequest.withState(RequestState.CAPTURED);
        return requests.replace(bestRequest.key(), bestRequest, captured) ? captured : null;
    }

    private double scoreRequest(RequestEntry request, List<ChunkCoordinate> playerChunks, long nowNanos) {
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

    private void captureAndGenerate(RequestEntry request) {
        ChunkSnapshotData snapshot;
        long captureStarted = System.nanoTime();
        try {
            snapshot = request.snapshotSupplier().get();
        } catch (Exception ex) {
            requests.remove(request.key());
            InertiaLogger.warn("Failed to capture chunk for " + request.worldName() + " at " + request.chunkX() + ", " + request.chunkZ(), ex);
            return;
        }
        totalCaptureNanos.addAndGet(System.nanoTime() - captureStarted);
        captureSamples.incrementAndGet();

        if (snapshot == null) {
            requests.remove(request.key());
            return;
        }

        int generationId = generationRevisions.computeIfAbsent(request.key(), unused -> new AtomicInteger()).get();
        long generateStarted = System.nanoTime();
        CompletableFuture<GreedyMeshData> future = generationQueue.submit(() -> generator.generate(snapshot, request.dirtyRegion()));
        RequestEntry withFuture = request.withSnapshot(snapshot).withFuture(future);
        requests.put(request.key(), withFuture);

        future.whenComplete((generatedData, throwable) -> {
            totalGenerateNanos.addAndGet(System.nanoTime() - generateStarted);
            generateSamples.incrementAndGet();

            RequestEntry current = requests.get(request.key());
            if (current == null) {
                return;
            }
            if (throwable != null) {
                requests.remove(request.key());
                InertiaLogger.warn("Failed to generate physics chunk at " + request.chunkX() + ", " + request.chunkZ(), throwable);
                return;
            }
            if (generationRevisions.computeIfAbsent(request.key(), unused -> new AtomicInteger()).get() != generationId) {
                requests.remove(request.key());
                return;
            }

            requests.put(request.key(), current.withState(RequestState.GENERATED));
            if (cache != null) {
                ChunkSnapshotData finalSnapshot = withFuture.snapshot();
                cacheIoExecutor.execute(() -> cache.put(
                        request.chunkX(),
                        request.chunkZ(),
                        new CachedChunkPhysicsData(generatedData, finalSnapshot.sectionFingerprints())
                ));
            }
            completeApply(requests.get(request.key()), generatedData);
        });
    }

    private void completeApply(RequestEntry entry, GreedyMeshData generatedData) {
        if (entry == null) {
            return;
        }
        long applyStarted = System.nanoTime();
        try {
            entry.onReady().accept(generatedData);
            totalApplyNanos.addAndGet(System.nanoTime() - applyStarted);
            applySamples.incrementAndGet();
            requests.put(entry.key(), entry.withState(RequestState.APPLIED));
        } finally {
            requests.remove(entry.key());
        }
    }

    @Override
    public void close() {
        requests.values().forEach(request -> {
            if (request.future() != null) {
                request.future().cancel(true);
            }
        });
        requests.clear();
        if (generationQueue != null) {
            generationQueue.close();
        }
        cacheIoExecutor.shutdownNow();
    }

    public enum GenerationRequestKind {
        DIRTY,
        GENERATE_ON_LOAD
    }

    public enum RequestState {
        REQUESTED,
        CAPTURED,
        GENERATED,
        APPLIED
    }

    public record ChunkCoordinate(int x, int z) {
    }

    public record CaptureMetrics(long captureQueueDepth,
                                 double averageCaptureMillis,
                                 double averageGenerateMillis,
                                 double averageApplyMillis,
                                 long skippedByBudgetCount,
                                 int generateQueueDepth,
                                 int generateInFlightJobs) {
    }

    private record RequestEntry(String worldName,
                                int chunkX,
                                int chunkZ,
                                long key,
                                Supplier<ChunkSnapshotData> snapshotSupplier,
                                Consumer<GreedyMeshData> onReady,
                                DirtyChunkRegion dirtyRegion,
                                GenerationRequestKind requestKind,
                                RequestState state,
                                ChunkSnapshotData snapshot,
                                long enqueueNanos,
                                CompletableFuture<GreedyMeshData> future) {
        private RequestEntry withState(RequestState newState) {
            return new RequestEntry(worldName, chunkX, chunkZ, key, snapshotSupplier, onReady, dirtyRegion, requestKind, newState, snapshot, enqueueNanos, future);
        }

        private RequestEntry withSnapshot(ChunkSnapshotData newSnapshot) {
            return new RequestEntry(worldName, chunkX, chunkZ, key, snapshotSupplier, onReady, dirtyRegion, requestKind, state, newSnapshot, enqueueNanos, future);
        }

        private RequestEntry withFuture(CompletableFuture<GreedyMeshData> newFuture) {
            return new RequestEntry(worldName, chunkX, chunkZ, key, snapshotSupplier, onReady, dirtyRegion, requestKind, state, snapshot, enqueueNanos, newFuture);
        }
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
