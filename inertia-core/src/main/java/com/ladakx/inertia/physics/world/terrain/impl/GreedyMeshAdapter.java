package com.ladakx.inertia.physics.world.terrain.impl;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import org.bukkit.Chunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GreedyMeshAdapter implements TerrainAdapter {

    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet();
    private PhysicsWorld world;

    @Override
    public void onEnable(PhysicsWorld world) {
        this.world = world;
        for (Chunk chunk : world.getWorldBukkit().getLoadedChunks()) {
            requestChunkGeneration(chunk.getX(), chunk.getZ());
        }
    }

    @Override
    public void onDisable() {
        queuedChunks.clear();
        world = null;
    }

    @Override
    public void onChunkLoad(int x, int z) {
        requestChunkGeneration(x, z);
    }

    @Override
    public void onChunkUnload(int x, int z) {
        queuedChunks.remove(ChunkUtils.getChunkKey(x, z));
    }

    private void requestChunkGeneration(int x, int z) {
        long key = ChunkUtils.getChunkKey(x, z);
        if (!queuedChunks.add(key) || world == null) {
            return;
        }

        world.schedulePhysicsTask(() -> {
            InertiaLogger.info("Queued greedy-mesh physics chunk generation at " + x + ", " + z);
            // TODO: integrate greedy-mesh terrain generation when available.
        });
    }
}
