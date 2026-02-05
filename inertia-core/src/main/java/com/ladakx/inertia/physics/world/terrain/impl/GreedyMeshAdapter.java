package com.ladakx.inertia.physics.world.terrain.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.infrastructure.nms.jolt.JoltTools;
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

import java.util.*;

public class GreedyMeshAdapter implements TerrainAdapter {

    private PhysicsWorld world;
    private ChunkPhysicsManager chunkPhysicsManager;
    private JoltTools joltTools;
    private final Map<Long, List<Integer>> chunkBodies = new HashMap<>();

    @Override
    public void onEnable(PhysicsWorld world) {
        this.world = world;
        InertiaConfig config = InertiaPlugin.getInstance().getConfigManager().getInertiaConfig();
        int workerThreads = config.PHYSICS.workerThreads;
        this.joltTools = InertiaPlugin.getInstance().getJoltTools();

        GenerationQueue generationQueue = new GenerationQueue(workerThreads);
        ChunkPhysicsCache cache = new ChunkPhysicsCache(InertiaPlugin.getInstance().getDataFolder());
        GreedyMeshGenerator generator = new GreedyMeshGenerator(
                InertiaPlugin.getInstance().getConfigManager().getBlocksConfig(),
                joltTools
        );

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
        joltTools = null;
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
        // Re-request immediately
        if (world.getWorldBukkit().isChunkLoaded(chunkX, chunkZ)) {
            requestChunkGeneration(chunkX, chunkZ);
        }
    }

    private void requestChunkGeneration(int x, int z) {
        if (world == null || chunkPhysicsManager == null) {
            return;
        }

        // --- Boundary Check Start ---
        com.ladakx.inertia.configuration.dto.WorldsConfig.WorldSizeSettings sizeSettings = world.getSettings().size();
        double minWorldX = sizeSettings.worldMin().xx();
        double minWorldZ = sizeSettings.worldMin().zz();
        double maxWorldX = sizeSettings.worldMax().xx();
        double maxWorldZ = sizeSettings.worldMax().zz();

        double chunkMinX = x * 16.0;
        double chunkMaxX = chunkMinX + 16.0;
        double chunkMinZ = z * 16.0;
        double chunkMaxZ = chunkMinZ + 16.0;

        boolean isOutside = chunkMaxX < minWorldX || chunkMinX > maxWorldX ||
                chunkMaxZ < minWorldZ || chunkMinZ > maxWorldZ;

        if (isOutside) {
            return;
        }
        // --- Boundary Check End ---

        String worldName = world.getWorldBukkit().getName();
        if (!world.getWorldBukkit().isChunkLoaded(x, z)) {
            return;
        }

        chunkPhysicsManager.requestChunkGeneration(
                worldName,
                x,
                z,
                () -> world.getWorldBukkit().getChunkAt(x, z),
                data -> {
                    if (world != null) {
                        world.schedulePhysicsTask(() -> applyMeshData(x, z, data));
                    }
                }
        );
    }

    // Helper record for grouping shapes by physics properties
    private record PhysicsProperties(float friction, float restitution) {}

    private void applyMeshData(int x, int z, GreedyMeshData data) {
        long key = ChunkUtils.getChunkKey(x, z);
        removeChunkBodies(key); // Cleanup previous version

        if (world == null || data.shapes().isEmpty()) {
            return;
        }

        // InertiaLogger.info("Applying greedy-mesh physics chunk at " + x + ", " + z + " with " + data.shapes().size() + " shapes.");

        BodyInterface bi = world.getBodyInterface();
        RVec3 worldOrigin = world.getOrigin();

        // Group shapes to reduce body count.
        // We create one body per unique set of (Friction, Restitution).
        Map<PhysicsProperties, StaticCompoundShapeSettings> groups = new HashMap<>();

        // Determine Chunk Origin in World Space (relative to Jolt Origin)
        // Body Position = (ChunkX*16, 0, ChunkZ*16) - WorldOrigin
        double chunkWorldX = x * 16.0;
        double chunkWorldZ = z * 16.0;
        double bodyPosX = chunkWorldX - worldOrigin.xx();
        double bodyPosY = -worldOrigin.yy(); // Assuming Y=0 base
        double bodyPosZ = chunkWorldZ - worldOrigin.zz();

        RVec3 bodyPosition = new RVec3(bodyPosX, bodyPosY, bodyPosZ);

        int chunkOffsetX = x << 4;
        int chunkOffsetZ = z << 4;

        for (GreedyMeshShape shapeData : data.shapes()) {
            if (shapeData.boundingBoxes().isEmpty()) continue;

            ConstShape subShape = buildShape(shapeData, chunkOffsetX, chunkOffsetZ);
            if (subShape == null) continue;

            // Grouping key
            PhysicsProperties props = new PhysicsProperties(shapeData.friction(), shapeData.restitution());
            StaticCompoundShapeSettings compoundSettings = groups.computeIfAbsent(props, k -> new StaticCompoundShapeSettings());

            // Calculate center of the sub-shape in absolute world coordinates
            double shapeCenterX = ((double) shapeData.minX() + shapeData.maxX()) * 0.5 + chunkOffsetX;
            double shapeCenterY = ((double) shapeData.minY() + shapeData.maxY()) * 0.5;
            double shapeCenterZ = ((double) shapeData.minZ() + shapeData.maxZ()) * 0.5 + chunkOffsetZ;

            // Calculate position relative to the Body Position (Chunk Corner)
            // LocalPos = AbsolutePos - ChunkCornerPos
            float localX = (float) (shapeCenterX - chunkWorldX);
            float localY = (float) (shapeCenterY); // - 0
            float localZ = (float) (shapeCenterZ - chunkWorldZ);

            compoundSettings.addShape(new Vec3(localX, localY, localZ), Quat.sIdentity(), subShape);
        }

        List<Integer> newBodyIds = new ArrayList<>();

        for (Map.Entry<PhysicsProperties, StaticCompoundShapeSettings> entry : groups.entrySet()) {
            PhysicsProperties props = entry.getKey();
            StaticCompoundShapeSettings settings = entry.getValue();

            // Create the compound shape
            ShapeResult result = settings.create();
            if (result.hasError()) {
                InertiaLogger.warn("Failed to create chunk compound shape at " + x + ", " + z + ": " + result.getError());
                continue;
            }

            ConstShape chunkShape = result.get();

            BodyCreationSettings bcs = new BodyCreationSettings();
            bcs.setPosition(bodyPosition);
            bcs.setMotionType(EMotionType.Static);
            bcs.setObjectLayer(PhysicsLayers.OBJ_STATIC);
            bcs.setShape(chunkShape);
            bcs.setFriction(props.friction());
            bcs.setRestitution(props.restitution());

            try {
                Body body = bi.createBody(bcs);
                bi.addBody(body, EActivation.DontActivate);
                world.registerSystemStaticBody(body.getId());
                newBodyIds.add(body.getId());
            } catch (Exception e) {
                InertiaLogger.error("Failed to create chunk body at " + x + ", " + z + " (Out of bodies?)");
            }
        }

        if (!newBodyIds.isEmpty()) {
            chunkBodies.put(key, newBodyIds);
        }
    }

    private ConstShape buildShape(GreedyMeshShape shapeData, int chunkOffsetX, int chunkOffsetZ) {
        List<SerializedBoundingBox> boxes = shapeData.boundingBoxes();
        if (boxes.isEmpty()) return null;

        // If it's a single box, just return the BoxShape directly
        if (boxes.size() == 1) {
            return createBoxShape(boxes.get(0), chunkOffsetX, chunkOffsetZ);
        }

        // If multiple boxes (complex shape for single block type), assume Compound
        // Center of this specific shape cluster
        double shapeCenterX = ((double) shapeData.minX() + shapeData.maxX()) * 0.5 + chunkOffsetX;
        double shapeCenterY = ((double) shapeData.minY() + shapeData.maxY()) * 0.5;
        double shapeCenterZ = ((double) shapeData.minZ() + shapeData.maxZ()) * 0.5 + chunkOffsetZ;

        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
        boolean added = false;

        for (SerializedBoundingBox box : boxes) {
            ConstShape child = createBoxShape(box, chunkOffsetX, chunkOffsetZ);
            if (child == null) continue;

            double boxCenterX = (box.minX() + box.maxX()) * 0.5 + chunkOffsetX;
            double boxCenterY = (box.minY() + box.maxY()) * 0.5;
            double boxCenterZ = (box.minZ() + box.maxZ()) * 0.5 + chunkOffsetZ;

            Vec3 localPos = new Vec3(
                    (float) (boxCenterX - shapeCenterX),
                    (float) (boxCenterY - shapeCenterY),
                    (float) (boxCenterZ - shapeCenterZ)
            );
            settings.addShape(localPos, Quat.sIdentity(), child);
            added = true;
        }

        if (!added) return null;

        ShapeResult result = settings.create();
        if (result.hasError()) return null;
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

        if (halfX <= 0.001f || halfY <= 0.001f || halfZ <= 0.001f) {
            return null;
        }
        return new BoxShape(new Vec3(halfX, halfY, halfZ));
    }

    private void removeChunkBodies(long key) {
        if (world == null) return;
        List<Integer> ids = chunkBodies.remove(key);
        if (ids == null || ids.isEmpty()) return;

        BodyInterface bi = world.getBodyInterface();
        for (int id : ids) {
            world.unregisterSystemStaticBody(id);
            try {
                bi.removeBody(id);
                bi.destroyBody(id);
            } catch (Exception ignored) {}
        }
    }
}