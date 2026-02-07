package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;
import org.bukkit.Chunk;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChunkPhysicsManager implements AutoCloseable {

    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, CompletableFuture<GreedyMeshData>> inFlight = new ConcurrentHashMap<>();
    private final GenerationQueue generationQueue;
    private final ChunkPhysicsCache cache;
    private final PhysicsGenerator<GreedyMeshData> generator;
    private final ExecutorService cacheIoExecutor;

    public ChunkPhysicsManager(GenerationQueue generationQueue, ChunkPhysicsCache cache, PhysicsGenerator<GreedyMeshData> generator) {
        this.generationQueue = generationQueue;
        this.cache = cache;
        this.generator = generator;
        this.cacheIoExecutor = Executors.newSingleThreadExecutor(new CacheIoThreadFactory());
    }

    public void requestChunkGeneration(String worldName,
                                       int chunkX,
                                       int chunkZ,
                                       Supplier<Chunk> chunkSupplier,
                                       Consumer<GreedyMeshData> onReady) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        if (!queuedChunks.add(key) || cache == null || generator == null || generationQueue == null) {
            return;
        }

        CompletableFuture
            .supplyAsync(() -> cache.get(chunkX, chunkZ), cacheIoExecutor)
            .whenComplete((cached, throwable) -> {
                if (!queuedChunks.contains(key)) {
                    return;
                }
                if (throwable != null) {
                    InertiaLogger.warn("Failed to read terrain cache at " + chunkX + ", " + chunkZ, throwable);
                } else if (cached != null && cached.isPresent()) {
                    queuedChunks.remove(key);
                    onReady.accept(cached.get());
                    return;
                }
                startGeneration(worldName, chunkX, chunkZ, chunkSupplier, onReady, key);
            });
    }

    public void cancelChunk(int chunkX, int chunkZ) {
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        queuedChunks.remove(key);
        CompletableFuture<GreedyMeshData> future = inFlight.remove(key);
        if (future != null) {
            future.cancel(true);
        }
    }

    public void invalidate(int chunkX, int chunkZ) {
        if (cache != null) {
            cacheIoExecutor.execute(() -> cache.invalidate(chunkX, chunkZ));
        }
    }

    public void invalidateAll() {
        if (cache != null) {
            cacheIoExecutor.execute(cache::invalidateAll);
        }
    }

    private void startGeneration(String worldName,
                                 int chunkX,
                                 int chunkZ,
                                 Supplier<Chunk> chunkSupplier,
                                 Consumer<GreedyMeshData> onReady,
                                 long key) {
        Chunk chunk;
        try {
            chunk = chunkSupplier.get();
        } catch (Exception ex) {
            queuedChunks.remove(key);
            InertiaLogger.warn("Failed to capture chunk for " + worldName + " at " + chunkX + ", " + chunkZ, ex);
            return;
        }
        if (chunk == null) {
            queuedChunks.remove(key);
            return;
        }

        CompletableFuture<GreedyMeshData> future = generationQueue.submit(() -> generator.generate(chunk));
        inFlight.put(key, future);
        future.whenComplete((data, throwable) -> {
            inFlight.remove(key);
            queuedChunks.remove(key);
            if (throwable != null) {
                InertiaLogger.warn("Failed to generate physics chunk at " + chunkX + ", " + chunkZ, throwable);
                return;
            }
            cache.put(chunkX, chunkZ, data);
            onReady.accept(data);
        });
    }

    @Override
    public void close() {
        inFlight.values().forEach(future -> future.cancel(true));
        inFlight.clear();
        queuedChunks.clear();
        if (generationQueue != null) {
            generationQueue.close();
        }
        cacheIoExecutor.shutdownNow();
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
