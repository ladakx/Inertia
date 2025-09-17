package com.ladakx.inertia.core.world;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.core.physics.PhysicsManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChunkManager {

    private final PhysicsManager physicsManager;
    private final ConcurrentHashMap<Long, ChunkMesh> loadedMeshes = new ConcurrentHashMap<>();
    private final ExecutorService meshGenerationExecutor = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            r -> new Thread(r, "Inertia-Mesher-Thread")
    );

    public ChunkManager(PhysicsManager physicsManager) {
        this.physicsManager = physicsManager;
    }

    public void loadChunk(Chunk chunk) {
        long chunkKey = getChunkKey(chunk);
        if (loadedMeshes.containsKey(chunkKey)) {
            return;
        }
        loadedMeshes.put(chunkKey, new ChunkMesh(chunkKey, -1)); // Placeholder
        meshGenerationExecutor.submit(() -> processChunk(chunk));
    }

    public void unloadChunk(Chunk chunk) {
        long chunkKey = getChunkKey(chunk);
        ChunkMesh mesh = loadedMeshes.remove(chunkKey);
        if (mesh != null && mesh.getBodyId() != -1) {
            physicsManager.removeBody(mesh.getBodyId());
        }
    }

    private void processChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        try (StaticCompoundShapeSettings compoundShapeSettings = new StaticCompoundShapeSettings()) {
            boolean hasBlocks = false;
            int minY = 0;
            int maxY = 256;
            try {
                minY = world.getMinHeight();
                maxY = world.getMaxHeight();
            } catch (NoSuchMethodError e) {
                // Fallback for older versions
            }

            for (int y = minY; y < maxY; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getType() != Material.AIR && block.getType().isSolid()) {
                            float localX = x + 0.5f;
                            float localY = y + 0.5f;
                            float localZ = z + 0.5f;

                            Vec3 halfExtents = new Vec3(0.5f, 0.5f, 0.5f);
                            try (BoxShapeSettings boxSettings = new BoxShapeSettings(halfExtents)) {
                                Vec3 translation = new Vec3(localX, localY, localZ);
                                Quat rotation = new Quat();
                                compoundShapeSettings.addShape(translation, rotation, boxSettings);
                                hasBlocks = true;
                            }
                        }
                    }
                }
            }

            if (hasBlocks) {
                physicsManager.queueCommand(() -> {
                    RVec3 position = new RVec3(chunkX * 16, 0, chunkZ * 16);
                    Quat rotation = new Quat();
                    try (BodyCreationSettings settings = new BodyCreationSettings(
                            compoundShapeSettings, position, rotation,
                            EMotionType.Static, (short) PhysicsManager.LAYER_NON_MOVING
                    )) {
                        int bodyId = physicsManager.getBodyInterface().createAndAddBody(settings, EActivation.DontActivate);
                        long chunkKey = getChunkKey(chunk);
                        loadedMeshes.put(chunkKey, new ChunkMesh(chunkKey, bodyId));
                    }
                });
            } else {
                loadedMeshes.remove(getChunkKey(chunk));
            }
        }
    }

    public void shutdown() {
        meshGenerationExecutor.shutdown();
        loadedMeshes.values().forEach(mesh -> {
            if (mesh.getBodyId() != -1) {
                physicsManager.removeBody(mesh.getBodyId());
            }
        });
        loadedMeshes.clear();
    }

    private long getChunkKey(Chunk chunk) {
        return (long) chunk.getX() & 0xFFFFFFFFL | ((long) chunk.getZ() & 0xFFFFFFFFL) << 32;
    }
}

