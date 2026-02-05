package com.ladakx.inertia.physics.world.terrain.impl;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.terrain.ChunkPhysicsCache;
import com.ladakx.inertia.physics.world.terrain.GenerationQueue;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshGenerator;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class GreedyMeshAdapter implements TerrainAdapter {

    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, CompletableFuture<GreedyMeshData>> inFlight = new ConcurrentHashMap<>();
    private PhysicsWorld world;
    private GenerationQueue generationQueue;
    private ChunkPhysicsCache cache;
    private GreedyMeshGenerator generator;

    @Override
    public void onEnable(PhysicsWorld world) {
        this.world = world;
        InertiaConfig config = InertiaPlugin.getInstance().getConfigManager().getInertiaConfig();
        int workerThreads = config.PHYSICS.workerThreads;
        this.generationQueue = new GenerationQueue(workerThreads);
        this.cache = new ChunkPhysicsCache(InertiaPlugin.getInstance().getDataFolder());
        this.generator = new GreedyMeshGenerator();
        for (Chunk chunk : world.getWorldBukkit().getLoadedChunks()) {
            requestChunkGeneration(chunk.getX(), chunk.getZ());
        }
    }

    @Override
    public void onDisable() {
        inFlight.values().forEach(future -> future.cancel(true));
        inFlight.clear();
        queuedChunks.clear();
        if (generationQueue != null) {
            generationQueue.close();
        }
        generationQueue = null;
        cache = null;
        generator = null;
        world = null;
    }

    @Override
    public void onChunkLoad(int x, int z) {
        requestChunkGeneration(x, z);
    }

    @Override
    public void onChunkUnload(int x, int z) {
        long key = ChunkUtils.getChunkKey(x, z);
        queuedChunks.remove(key);
        CompletableFuture<GreedyMeshData> future = inFlight.remove(key);
        if (future != null) {
            future.cancel(true);
        }
    }

    @Override
    public void onBlockChange(int x, int y, int z) {
        if (world == null || cache == null) {
            return;
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        cache.invalidate(world.getWorldBukkit().getName(), chunkX, chunkZ);
    }

    private void requestChunkGeneration(int x, int z) {
        long key = ChunkUtils.getChunkKey(x, z);
        if (!queuedChunks.add(key) || world == null || cache == null || generator == null || generationQueue == null) {
            return;
        }

        String worldName = world.getWorldBukkit().getName();
        Optional<GreedyMeshData> cached = cache.get(worldName, x, z);
        if (cached.isPresent()) {
            queuedChunks.remove(key);
            world.schedulePhysicsTask(() -> applyMeshData(x, z, cached.get()));
            return;
        }

        if (!world.getWorldBukkit().isChunkLoaded(x, z)) {
            queuedChunks.remove(key);
            return;
        }

        ChunkSnapshot snapshot = world.getWorldBukkit().getChunkAt(x, z).getChunkSnapshot(true, true, false);
        CompletableFuture<GreedyMeshData> future = generationQueue.submit(() -> generator.generate(snapshot));
        inFlight.put(key, future);
        future.whenComplete((data, throwable) -> {
            inFlight.remove(key);
            queuedChunks.remove(key);
            if (throwable != null) {
                InertiaLogger.warn("Failed to generate greedy-mesh physics chunk at " + x + ", " + z, throwable);
                return;
            }
            cache.put(worldName, x, z, data);
            if (world != null) {
                world.schedulePhysicsTask(() -> applyMeshData(x, z, data));
            }
        });
    }

    private void applyMeshData(int x, int z, GreedyMeshData data) {
        InertiaLogger.info("Applying greedy-mesh physics chunk at " + x + ", " + z + " with " + data.shapes().size() + " shapes.");
        // TODO: create static bodies for cached/generated shapes.
    }
}
