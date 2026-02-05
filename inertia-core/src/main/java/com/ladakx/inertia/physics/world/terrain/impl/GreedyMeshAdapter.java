package com.ladakx.inertia.physics.world.terrain.impl;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.ConstShape;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.terrain.ChunkPhysicsCache;
import com.ladakx.inertia.physics.world.terrain.ChunkPhysicsManager;
import com.ladakx.inertia.physics.world.terrain.GenerationQueue;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshGenerator;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshShape;
import com.ladakx.inertia.physics.world.terrain.greedy.SerializedBoundingBox;
import org.bukkit.Chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GreedyMeshAdapter implements TerrainAdapter {

    private PhysicsWorld world;
    private ChunkPhysicsManager chunkPhysicsManager;
    private final Map<Long, List<Integer>> chunkBodies = new HashMap<>();

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
        if (world != null) {
            for (long key : new ArrayList<>(chunkBodies.keySet())) {
                removeChunkBodies(key);
            }
        }
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
        if (world != null) {
            long key = ChunkUtils.getChunkKey(x, z);
            world.schedulePhysicsTask(() -> removeChunkBodies(key));
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
        if (world == null) {
            return;
        }

        long key = ChunkUtils.getChunkKey(x, z);
        removeChunkBodies(key);

        if (data.shapes().isEmpty()) {
            return;
        }

        BodyInterface bi = world.getBodyInterface();
        RVec3 origin = world.getOrigin();
        List<Integer> bodyIds = new ArrayList<>();
        int chunkOffsetX = x << 4;
        int chunkOffsetZ = z << 4;

        for (GreedyMeshShape shapeData : data.shapes()) {
            if (shapeData.boundingBoxes().isEmpty()) {
                continue;
            }
            ConstShape shape = buildShape(shapeData, chunkOffsetX, chunkOffsetZ);
            if (shape == null) {
                continue;
            }

            double shapeCenterX = ((double) shapeData.minX() + shapeData.maxX()) * 0.5 + chunkOffsetX;
            double shapeCenterY = ((double) shapeData.minY() + shapeData.maxY()) * 0.5;
            double shapeCenterZ = ((double) shapeData.minZ() + shapeData.maxZ()) * 0.5 + chunkOffsetZ;

            double joltX = shapeCenterX - origin.xx();
            double joltY = shapeCenterY - origin.yy();
            double joltZ = shapeCenterZ - origin.zz();

            BodyCreationSettings bcs = new BodyCreationSettings();
            bcs.setPosition(new RVec3(joltX, joltY, joltZ));
            bcs.setMotionType(EMotionType.Static);
            bcs.setObjectLayer(PhysicsLayers.OBJ_STATIC);
            bcs.setShape(shape);
            bcs.setFriction(shapeData.friction());
            bcs.setRestitution(shapeData.restitution());
            bcs.setDensity(shapeData.density());

            Body body = bi.createBody(bcs);
            bi.addBody(body, EActivation.DontActivate);
            world.registerSystemStaticBody(body.getId());
            bodyIds.add(body.getId());
        }

        if (!bodyIds.isEmpty()) {
            chunkBodies.put(key, bodyIds);
        }
    }

    private ConstShape buildShape(GreedyMeshShape shapeData, int chunkOffsetX, int chunkOffsetZ) {
        List<SerializedBoundingBox> boxes = shapeData.boundingBoxes();
        if (boxes.size() == 1) {
            SerializedBoundingBox box = boxes.get(0);
            return createBoxShape(box, chunkOffsetX, chunkOffsetZ);
        }

        double shapeCenterX = ((double) shapeData.minX() + shapeData.maxX()) * 0.5 + chunkOffsetX;
        double shapeCenterY = ((double) shapeData.minY() + shapeData.maxY()) * 0.5;
        double shapeCenterZ = ((double) shapeData.minZ() + shapeData.maxZ()) * 0.5 + chunkOffsetZ;

        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
        boolean added = false;
        for (SerializedBoundingBox box : boxes) {
            ConstShape child = createBoxShape(box, chunkOffsetX, chunkOffsetZ);
            if (child == null) {
                continue;
            }
            double boxCenterX = (box.minX() + box.maxX()) * 0.5 + chunkOffsetX;
            double boxCenterY = (box.minY() + box.maxY()) * 0.5;
            double boxCenterZ = (box.minZ() + box.maxZ()) * 0.5 + chunkOffsetZ;
            Vec3 localPos = new Vec3(
                    (float) (boxCenterX - shapeCenterX),
                    (float) (boxCenterY - shapeCenterY),
                    (float) (boxCenterZ - shapeCenterZ)
            );
            settings.addShape(localPos, new Quat(0f, 0f, 0f, 1f), child);
            added = true;
        }
        if (!added) {
            return null;
        }

        ShapeResult result = settings.create();
        if (result.hasError()) {
            InertiaLogger.warn("Failed to build compound shape: " + result.getError());
            return null;
        }
        return result.get();
    }

    private ConstShape createBoxShape(SerializedBoundingBox box, int chunkOffsetX, int chunkOffsetZ) {
        float minX = box.minX() + chunkOffsetX;
        float minY = box.minY();
        float minZ = box.minZ() + chunkOffsetZ;
        float maxX = box.maxX() + chunkOffsetX;
        float maxY = box.maxY();
        float maxZ = box.maxZ() + chunkOffsetZ;
        float halfX = (maxX - minX) * 0.5f;
        float halfY = (maxY - minY) * 0.5f;
        float halfZ = (maxZ - minZ) * 0.5f;
        if (halfX <= 0f || halfY <= 0f || halfZ <= 0f) {
            return null;
        }
        return new BoxShape(new Vec3(halfX, halfY, halfZ));
    }

    private void removeChunkBodies(long key) {
        if (world == null) {
            return;
        }
        List<Integer> ids = chunkBodies.remove(key);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        BodyInterface bi = world.getBodyInterface();
        for (int id : ids) {
            world.unregisterSystemStaticBody(id);
            bi.removeBody(id);
            bi.destroyBody(id);
        }
    }
}
