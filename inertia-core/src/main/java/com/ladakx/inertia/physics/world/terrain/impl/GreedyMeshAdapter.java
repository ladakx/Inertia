package com.ladakx.inertia.physics.world.terrain.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.BlocksConfig;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
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
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GreedyMeshAdapter implements TerrainAdapter {

    private PhysicsWorld world;
    private ChunkPhysicsManager chunkPhysicsManager;
    private JoltTools joltTools;
    private BlocksConfig blocksConfig;
    private final Map<Long, List<Integer>> chunkBodies = new HashMap<>();
    private final Set<Long> loadedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, BukkitTask> pendingUpdates = new HashMap<>();
    private WorldsConfig.ChunkManagementSettings chunkSettings;

    @Override
    public void onEnable(PhysicsWorld world) {
        this.world = world;
        InertiaConfig config = InertiaPlugin.getInstance().getConfigManager().getInertiaConfig();
        int workerThreads = config.PHYSICS.workerThreads;
        this.joltTools = InertiaPlugin.getInstance().getJoltTools();
        this.blocksConfig = InertiaPlugin.getInstance().getConfigManager().getBlocksConfig();

        this.chunkSettings = world.getSettings().chunkManagement();
        WorldsConfig.GreedyMeshingSettings meshingSettings = world.getSettings().simulation().greedyMeshing();

        GenerationQueue generationQueue = new GenerationQueue(workerThreads);
        ChunkPhysicsCache cache = new ChunkPhysicsCache(InertiaPlugin.getInstance().getDataFolder());

        GreedyMeshGenerator generator = new GreedyMeshGenerator(
                blocksConfig,
                joltTools,
                meshingSettings
        );

        this.chunkPhysicsManager = new ChunkPhysicsManager(generationQueue, cache, generator);

        if (chunkSettings.generateOnLoad()) {
            for (Chunk chunk : world.getWorldBukkit().getLoadedChunks()) {
                onChunkLoad(chunk.getX(), chunk.getZ());
            }
        }
    }

    @Override
    public void onDisable() {
        pendingUpdates.values().forEach(BukkitTask::cancel);
        pendingUpdates.clear();

        if (world != null) {
            for (long key : new ArrayList<>(chunkBodies.keySet())) {
                removeChunkBodies(key);
            }
        }
        if (chunkPhysicsManager != null) {
            chunkPhysicsManager.close();
        }
        loadedChunks.clear();
        chunkPhysicsManager = null;
        joltTools = null;
        world = null;
    }

    @Override
    public void onChunkLoad(int x, int z) {
        long key = ChunkUtils.getChunkKey(x, z);
        loadedChunks.add(key);

        if (chunkSettings.generateOnLoad()) {
            requestChunkGeneration(x, z);
        }
    }

    @Override
    public void onChunkUnload(int x, int z) {
        long key = ChunkUtils.getChunkKey(x, z);
        loadedChunks.remove(key);

        BukkitTask task = pendingUpdates.remove(key);
        if (task != null) task.cancel();

        if (chunkPhysicsManager != null) {
            chunkPhysicsManager.cancelChunk(x, z);
        }

        if (world != null && chunkSettings.removeOnUnload()) {
            world.schedulePhysicsTask(() -> removeChunkBodies(key));
        }
    }

    @Override
    public void onBlockChange(int x, int y, int z, Material oldMaterial, Material newMaterial) {
        if (world == null || chunkPhysicsManager == null || !chunkSettings.updateOnBlockChange()) {
            return;
        }

        if (!hasPhysicalProfile(oldMaterial) && !hasPhysicalProfile(newMaterial)) {
            return;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);

        if (!loadedChunks.contains(key)) return;

        chunkPhysicsManager.invalidate(world.getWorldBukkit().getName(), chunkX, chunkZ);

        BukkitTask existing = pendingUpdates.get(key);
        if (existing != null) {
            existing.cancel();
        }

        int delay = Math.max(1, chunkSettings.updateDebounceTicks());
        BukkitTask task = Bukkit.getScheduler().runTaskLater(InertiaPlugin.getInstance(), () -> {
            pendingUpdates.remove(key);
            if (loadedChunks.contains(key)) {
                requestChunkGeneration(chunkX, chunkZ);
            }
        }, delay);

        pendingUpdates.put(key, task);
    }

    @Override
    public void onChunkChange(int x, int z) {
        if (world == null || chunkPhysicsManager == null) {
            return;
        }
        long key = ChunkUtils.getChunkKey(x, z);
        if (!loadedChunks.contains(key)) return;
        chunkPhysicsManager.invalidate(world.getWorldBukkit().getName(), x, z);
        BukkitTask pending = pendingUpdates.remove(key);
        if (pending != null) pending.cancel();
        requestChunkGeneration(x, z);
    }

    private void requestChunkGeneration(int x, int z) {
        if (world == null || chunkPhysicsManager == null) {
            return;
        }

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

        if (!world.getWorldBukkit().isChunkLoaded(x, z)) {
            return;
        }

        String worldName = world.getWorldBukkit().getName();
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

    private record PhysicsProperties(float friction, float restitution) {}

    private boolean hasPhysicalProfile(Material material) {
        if (material == null || blocksConfig == null) {
            return false;
        }
        return blocksConfig.find(material).isPresent();
    }

    private void applyMeshData(int x, int z, GreedyMeshData data) {
        long key = ChunkUtils.getChunkKey(x, z);

        if (!loadedChunks.contains(key)) {
            return;
        }

        // 1. Remove old bodies
        removeChunkBodies(key);

        if (world == null || data.shapes().isEmpty()) {
            // Even if empty, we might need to wake up bodies (e.g. platform removed entirely)
            activateBodiesInChunk(x, z);
            return;
        }

        BodyInterface bi = world.getBodyInterface();
        RVec3 worldOrigin = world.getOrigin();

        Map<PhysicsProperties, StaticCompoundShapeSettings> groups = new HashMap<>();

        double chunkWorldX = x * 16.0;
        double chunkWorldZ = z * 16.0;
        double bodyPosX = chunkWorldX - worldOrigin.xx();
        double bodyPosY = -worldOrigin.yy();
        double bodyPosZ = chunkWorldZ - worldOrigin.zz();

        RVec3 bodyPosition = new RVec3(bodyPosX, bodyPosY, bodyPosZ);

        int chunkOffsetX = x << 4;
        int chunkOffsetZ = z << 4;

        Map<HalfExtentsKey, ConstShape> boxShapeCache = new HashMap<>();

        for (GreedyMeshShape shapeData : data.shapes()) {
            if (shapeData.boundingBoxes().isEmpty()) continue;

            PhysicsProperties props = new PhysicsProperties(shapeData.friction(), shapeData.restitution());
            StaticCompoundShapeSettings compoundSettings = groups.computeIfAbsent(props, k -> new StaticCompoundShapeSettings());

            if (shapeData.boundingBoxes().size() > 1) {
                for (SerializedBoundingBox box : shapeData.boundingBoxes()) {
                    ConstShape child = createBoxShape(box, chunkOffsetX, chunkOffsetZ, boxShapeCache);
                    if (child == null) continue;

                    double boxCenterX = (box.minX() + box.maxX()) * 0.5 + chunkOffsetX;
                    double boxCenterY = (box.minY() + box.maxY()) * 0.5;
                    double boxCenterZ = (box.minZ() + box.maxZ()) * 0.5 + chunkOffsetZ;

                    float localX = (float) (boxCenterX - chunkWorldX);
                    float localY = (float) (boxCenterY);
                    float localZ = (float) (boxCenterZ - chunkWorldZ);

                    compoundSettings.addShape(new Vec3(localX, localY, localZ), Quat.sIdentity(), child);
                }
                continue;
            }

            ConstShape subShape = buildShape(shapeData, chunkOffsetX, chunkOffsetZ, boxShapeCache);
            if (subShape == null) continue;

            double shapeCenterX = ((double) shapeData.minX() + shapeData.maxX()) * 0.5 + chunkOffsetX;
            double shapeCenterY = ((double) shapeData.minY() + shapeData.maxY()) * 0.5;
            double shapeCenterZ = ((double) shapeData.minZ() + shapeData.maxZ()) * 0.5 + chunkOffsetZ;

            float localX = (float) (shapeCenterX - chunkWorldX);
            float localY = (float) (shapeCenterY);
            float localZ = (float) (shapeCenterZ - chunkWorldZ);

            compoundSettings.addShape(new Vec3(localX, localY, localZ), Quat.sIdentity(), subShape);
        }

        List<Integer> newBodyIds = new ArrayList<>();

        for (Map.Entry<PhysicsProperties, StaticCompoundShapeSettings> entry : groups.entrySet()) {
            PhysicsProperties props = entry.getKey();
            StaticCompoundShapeSettings settings = entry.getValue();

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
                InertiaLogger.error("Failed to create chunk body at " + x + ", " + z, e);
            }
        }

        if (!newBodyIds.isEmpty()) {
            chunkBodies.put(key, newBodyIds);
        }

        // 2. Wake up any dynamic bodies in this chunk area
        // This ensures floating objects fall if support was removed/changed
        activateBodiesInChunk(x, z);
    }

    private void activateBodiesInChunk(int chunkX, int chunkZ) {
        if (world == null) return;

        com.github.stephengold.joltjni.PhysicsSystem system = world.getPhysicsSystem();
        BodyInterface bi = system.getBodyInterfaceNoLock();
        RVec3 origin = world.getOrigin();
        com.ladakx.inertia.configuration.dto.WorldsConfig.WorldSizeSettings sizeSettings = world.getSettings().size();

        double minX = (chunkX * 16.0) - origin.xx();
        double minZ = (chunkZ * 16.0) - origin.zz();

        double minY = sizeSettings.localMin().getY();
        double maxY = sizeSettings.localMax().getY();

        // Expand slightly to catch bodies on boundary
        double expand = 0.5;

        Vec3 boxMin = new Vec3((float)(minX - expand), (float)(minY - expand), (float)(minZ - expand));
        Vec3 boxMax = new Vec3((float)(minX + 16.0 + expand), (float)(maxY + expand), (float)(minZ + 16.0 + expand));

        AaBox box = new AaBox(boxMin, boxMax);

        try {
            try (AllHitCollideShapeBodyCollector collector = new AllHitCollideShapeBodyCollector();
                 SpecifiedBroadPhaseLayerFilter bpFilter = new SpecifiedBroadPhaseLayerFilter(0);
                 SpecifiedObjectLayerFilter objFilter = new SpecifiedObjectLayerFilter(PhysicsLayers.OBJ_MOVING)) {

                system.getBroadPhaseQuery().collideAaBox(box, collector, bpFilter, objFilter);
                int[] bodyIds = collector.getHits();
                if (bodyIds == null || bodyIds.length == 0) return;

                for (int id : bodyIds) {
                    if (id == 0) continue;
                    try {
                        if (bi.getMotionType(id) == EMotionType.Dynamic && !bi.isSensor(id)) {
                            bi.activateBody(id);
                        }
                    } catch (Exception ignored) {
                        // Body might have been removed between query and activation
                    }
                }
            }
        } catch (Exception e) {
            InertiaLogger.warn("Failed to wake bodies in chunk " + chunkX + "," + chunkZ + ": " + e.getMessage());
        } finally {
            box.close();
        }
    }

    private ConstShape buildShape(GreedyMeshShape shapeData, int chunkOffsetX, int chunkOffsetZ, Map<HalfExtentsKey, ConstShape> boxShapeCache) {
        List<SerializedBoundingBox> boxes = shapeData.boundingBoxes();
        if (boxes.isEmpty()) return null;

        if (boxes.size() == 1) {
            return createBoxShape(boxes.get(0), chunkOffsetX, chunkOffsetZ, boxShapeCache);
        }

        return null;
    }

    private ConstShape createBoxShape(SerializedBoundingBox box, int chunkOffsetX, int chunkOffsetZ, Map<HalfExtentsKey, ConstShape> boxShapeCache) {
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
        HalfExtentsKey key = HalfExtentsKey.of(halfX, halfY, halfZ);
        return boxShapeCache.computeIfAbsent(key, ignored -> new BoxShape(new Vec3(halfX, halfY, halfZ)));
    }

    private record HalfExtentsKey(int halfXBits, int halfYBits, int halfZBits) {
        private static HalfExtentsKey of(float halfX, float halfY, float halfZ) {
            return new HalfExtentsKey(Float.floatToIntBits(halfX), Float.floatToIntBits(halfY), Float.floatToIntBits(halfZ));
        }
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
