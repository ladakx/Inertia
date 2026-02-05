package com.ladakx.inertia.physics.world.terrain.impl;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.terrain.ChunkPhysicsCache;
import com.ladakx.inertia.physics.world.terrain.ChunkPhysicsManager;
import com.ladakx.inertia.physics.world.terrain.GenerationQueue;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshGenerator;
import org.bukkit.Chunk;

public class GreedyMeshAdapter implements TerrainAdapter {

    private PhysicsWorld world;
    private ChunkPhysicsManager chunkPhysicsManager;

    @Override
    public void onEnable(PhysicsWorld world) {
        this.world = world;
        InertiaConfig config = InertiaPlugin.getInstance().getConfigManager().getInertiaConfig();
        int workerThreads = config.PHYSICS.workerThreads;
        GenerationQueue generationQueue = new GenerationQueue(workerThreads);
        ChunkPhysicsCache cache = new ChunkPhysicsCache(InertiaPlugin.getInstance().getDataFolder());
        GreedyMeshGenerator generator = new GreedyMeshGenerator(InertiaPlugin.getInstance().getConfigManager().getBlocksConfig());
        this.chunkPhysicsManager = new ChunkPhysicsManager(generationQueue, cache, generator);
        for (Chunk chunk : world.getWorldBukkit().getLoadedChunks()) {
            requestChunkGeneration(chunk.getX(), chunk.getZ());
        }
    }

    @Override
    public void onDisable() {
        if (chunkPhysicsManager != null) {
            chunkPhysicsManager.close();
        }
        chunkPhysicsManager = null;
        world = null;
    }

    @Override
    public void onChunkLoad(int x, int z) {
        requestChunkGeneration(x, z);
    }

    @Override
    public void onChunkUnload(int x, int z) {
        if (chunkPhysicsManager != null) {
            chunkPhysicsManager.cancelChunk(x, z);
        }
    }

    @Override
    public void onBlockChange(int x, int y, int z) {
        if (world == null || chunkPhysicsManager == null) {
            return;
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        chunkPhysicsManager.invalidate(world.getWorldBukkit().getName(), chunkX, chunkZ);
    }

    private void requestChunkGeneration(int x, int z) {
        if (world == null || chunkPhysicsManager == null) {
            return;
        }

        String worldName = world.getWorldBukkit().getName();
        if (!world.getWorldBukkit().isChunkLoaded(x, z)) {
            return;
        }

        chunkPhysicsManager.requestChunkGeneration(
                worldName,
                x,
                z,
                () -> world.getWorldBukkit().getChunkAt(x, z).getChunkSnapshot(true, true, false),
                data -> {
                    if (world != null) {
                        world.schedulePhysicsTask(() -> applyMeshData(x, z, data));
                    }
                }
        );
    }

    private void applyMeshData(int x, int z, GreedyMeshData data) {
        InertiaLogger.info("Applying greedy-mesh physics chunk at " + x + ", " + z + " with " + data.shapes().size() + " shapes.");
        // TODO: create static bodies for cached/generated shapes.
    }
}
