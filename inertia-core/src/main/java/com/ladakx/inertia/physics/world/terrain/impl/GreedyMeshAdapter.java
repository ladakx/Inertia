package com.ladakx.inertia.physics.world.terrain.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
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
import com.ladakx.inertia.physics.world.terrain.ChunkSnapshotData;
import com.ladakx.inertia.physics.world.terrain.GenerationQueue;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshGenerator;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshShape;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class GreedyMeshAdapter implements TerrainAdapter {
    private PhysicsWorld world;
    private ChunkPhysicsManager chunkPhysicsManager;
    private JoltTools joltTools;
    private BlocksConfig blocksConfig;
    private final Map<Long, List<Integer>> chunkBodies = new HashMap<>();
    private final Set<Long> loadedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, BukkitTask> pendingUpdates = new HashMap<>();
    private final Map<Long, PendingMesh> pendingMeshData = new ConcurrentHashMap<>();
    private WorldsConfig.ChunkManagementSettings chunkSettings;
    private final AtomicLong meshSequence = new AtomicLong();
    private UUID meshApplyTaskId;

    @Override
    public void onEnable(PhysicsWorld world) {
        this.world = world;
        InertiaConfig config = InertiaPlugin.getInstance().getConfigManager().getInertiaConfig();
        int workerThreads = config.PHYSICS.workerThreads;
        InertiaConfig.PhysicsSettings.ChunkCacheSettings cacheSettings = config.PHYSICS.CHUNK_CACHE;

        this.joltTools = InertiaPlugin.getInstance().getJoltTools();
        this.blocksConfig = InertiaPlugin.getInstance().getConfigManager().getBlocksConfig();
        this.chunkSettings = world.getSettings().chunkManagement();
        WorldsConfig.GreedyMeshingSettings meshingSettings = world.getSettings().simulation().greedyMeshing();

        GenerationQueue generationQueue = new GenerationQueue(workerThreads);
        File worldFolder = world.getWorldBukkit().getWorldFolder();
        File cacheDir = new File(worldFolder, "physics");
        Duration cacheTtl = Duration.ofSeconds(Math.max(0, cacheSettings.ttlSeconds));
        ChunkPhysicsCache cache = new ChunkPhysicsCache(cacheDir, cacheSettings.maxEntries, cacheTtl);

        GreedyMeshGenerator generator = new GreedyMeshGenerator(
                blocksConfig,
                joltTools,
                meshingSettings
        );

        this.chunkPhysicsManager = new ChunkPhysicsManager(generationQueue, cache, generator, this::executeOnMainThread);
        this.meshApplyTaskId = world.addTickTask(this::processPendingMeshes);

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
        if (meshApplyTaskId != null) {
            world.removeTickTask(meshApplyTaskId);
            meshApplyTaskId = null;
        }
        if (chunkPhysicsManager != null) {
            chunkPhysicsManager.close();
        }
        loadedChunks.clear();
        pendingMeshData.clear();
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
        pendingMeshData.remove(key);

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
        if (world == null || chunkPhysicsManager == null || !chunkSettings.updateOnBlockChange()) return;
        if (!hasPhysicalProfile(oldMaterial) && !hasPhysicalProfile(newMaterial)) return;

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        if (!loadedChunks.contains(key)) return;

        chunkPhysicsManager.invalidate(chunkX, chunkZ);

        BukkitTask existing = pendingUpdates.get(key);
        if (existing != null) existing.cancel();

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
        if (world == null || chunkPhysicsManager == null) return;
        long key = ChunkUtils.getChunkKey(x, z);
        if (!loadedChunks.contains(key)) return;

        chunkPhysicsManager.invalidate(x, z);
        BukkitTask pending = pendingUpdates.remove(key);
        if (pending != null) pending.cancel();

        requestChunkGeneration(x, z);
    }

    private void requestChunkGeneration(int x, int z) {
        if (world == null || chunkPhysicsManager == null) return;

        // Проверка границ мира
        com.ladakx.inertia.configuration.dto.WorldsConfig.WorldSizeSettings sizeSettings = world.getSettings().size();
        double minWorldX = sizeSettings.worldMin().xx();
        double minWorldZ = sizeSettings.worldMin().zz();
        double maxWorldX = sizeSettings.worldMax().xx();
        double maxWorldZ = sizeSettings.worldMax().zz();

        if ((x + 1) * 16.0 < minWorldX || x * 16.0 > maxWorldX || (z + 1) * 16.0 < minWorldZ || z * 16.0 > maxWorldZ) {
            return;
        }

        if (!world.getWorldBukkit().isChunkLoaded(x, z)) return;

        String worldName = world.getWorldBukkit().getName();
        chunkPhysicsManager.requestChunkGeneration(
                worldName, x, z,
                () -> ChunkSnapshotData.capture(world.getWorldBukkit().getChunkAt(x, z), joltTools),
                data -> {
                    if (world != null) {
                        enqueueMeshData(x, z, data);
                    }
                }
        );
    }

    private void enqueueMeshData(int x, int z, GreedyMeshData data) {
        long key = ChunkUtils.getChunkKey(x, z);
        pendingMeshData.put(key, new PendingMesh(x, z, data, meshSequence.incrementAndGet()));
    }

    private void processPendingMeshes() {
        if (world == null || pendingMeshData.isEmpty()) {
            return;
        }
        int limit = Math.max(1, chunkSettings.maxMeshAppliesPerTick());
        Collection<PendingMesh> pendingValues = pendingMeshData.values();
        if (pendingValues.isEmpty()) {
            return;
        }
        List<Location> playerLocations = world.getWorldBukkit().getPlayers().stream()
                .map(player -> player.getEyeLocation())
                .toList();
        List<PendingMesh> pending = selectPendingMeshes(pendingValues, limit, playerLocations);

        int applied = 0;
        for (PendingMesh entry : pending) {
            if (applied >= limit) {
                break;
            }
            long key = ChunkUtils.getChunkKey(entry.x(), entry.z());
            if (!loadedChunks.contains(key)) {
                pendingMeshData.remove(key);
                continue;
            }
            if (pendingMeshData.remove(key, entry)) {
                applyMeshData(entry.x(), entry.z(), entry.data());
                applied++;
            }
        }
    }

    private double distanceSqToNearestPlayer(PendingMesh entry, List<Location> playerLocations) {
        if (playerLocations.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double chunkCenterX = entry.x() * 16.0 + 8.0;
        double chunkCenterZ = entry.z() * 16.0 + 8.0;
        double minDistance = Double.MAX_VALUE;
        for (Location location : playerLocations) {
            double dx = chunkCenterX - location.getX();
            double dz = chunkCenterZ - location.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < minDistance) {
                minDistance = distance;
            }
        }
        return minDistance;
    }

    private List<PendingMesh> selectPendingMeshes(Collection<PendingMesh> pendingValues,
                                                  int limit,
                                                  List<Location> playerLocations) {
        if (playerLocations.isEmpty()) {
            return selectBySequence(pendingValues, limit);
        }
        return selectByDistance(pendingValues, limit, playerLocations);
    }

    private List<PendingMesh> selectBySequence(Collection<PendingMesh> pendingValues, int limit) {
        Comparator<PendingMesh> comparator = Comparator.comparingLong(PendingMesh::sequence);
        PriorityQueue<PendingMesh> heap = new PriorityQueue<>(comparator.reversed());
        for (PendingMesh entry : pendingValues) {
            heap.offer(entry);
            if (heap.size() > limit) {
                heap.poll();
            }
        }
        List<PendingMesh> selected = new ArrayList<>(heap);
        selected.sort(comparator);
        return selected;
    }

    private List<PendingMesh> selectByDistance(Collection<PendingMesh> pendingValues,
                                               int limit,
                                               List<Location> playerLocations) {
        Comparator<ScoredPendingMesh> comparator = Comparator
                .comparingDouble(ScoredPendingMesh::distance)
                .thenComparingLong(entry -> entry.pending().sequence());
        PriorityQueue<ScoredPendingMesh> heap = new PriorityQueue<>(comparator.reversed());
        for (PendingMesh entry : pendingValues) {
            double distance = distanceSqToNearestPlayer(entry, playerLocations);
            heap.offer(new ScoredPendingMesh(entry, distance));
            if (heap.size() > limit) {
                heap.poll();
            }
        }
        List<ScoredPendingMesh> scored = new ArrayList<>(heap);
        scored.sort(comparator);
        List<PendingMesh> selected = new ArrayList<>(scored.size());
        for (ScoredPendingMesh entry : scored) {
            selected.add(entry.pending());
        }
        return selected;
    }

    private boolean hasPhysicalProfile(Material material) {
        if (material == null || blocksConfig == null) return false;
        return blocksConfig.find(material).isPresent();
    }

    /**
     * Основной метод оптимизации:
     * Группирует все треугольники с одинаковыми физ. свойствами и создает MeshShape.
     */
    private void applyMeshData(int x, int z, GreedyMeshData data) {
        long key = ChunkUtils.getChunkKey(x, z);
        if (!loadedChunks.contains(key)) return;

        removeChunkBodies(key);

        if (world == null || data.shapes().isEmpty()) {
            return;
        }

        BodyInterface bi = world.getBodyInterface();
        RVec3 worldOrigin = world.getOrigin();

        // Координаты чанка в мире Jolt (относительно origin)
        double chunkWorldX = x * 16.0 - worldOrigin.xx();
        double chunkWorldZ = z * 16.0 - worldOrigin.zz();
        double chunkWorldY = -worldOrigin.yy();

        RVec3 bodyPosition = new RVec3(chunkWorldX, chunkWorldY, chunkWorldZ);

        // Группировка по свойствам
        Map<String, MeshGroup> groupedVertices = new HashMap<>();

        for (GreedyMeshShape shapeData : data.shapes()) {
            if (shapeData.vertices().length == 0) continue;
            MeshGroup group = groupedVertices.computeIfAbsent(
                    shapeData.materialId(),
                    key -> new MeshGroup(shapeData.friction(), shapeData.restitution())
            );
            group.vertices().add(shapeData.vertices());
        }

        List<Integer> newBodyIds = new ArrayList<>();

        for (Map.Entry<String, MeshGroup> entry : groupedVertices.entrySet()) {
            MeshGroup group = entry.getValue();
            List<float[]> allVertices = group.vertices();

            // 1. Подсчет общего количества треугольников
            int totalTriangles = 0;
            for (float[] verts : allVertices) {
                totalTriangles += verts.length / 9; // 9 float на треугольник
            }

            if (totalTriangles == 0) continue;

            // 2. Создание списков
            // ВАЖНО: VertexList внутри использует DirectBuffer. Если использовать pushBack(),
            // он часто делает resize() -> аллоцирует новые DirectBuffer и может быстро привести к Direct OOM.
            // Поэтому: заранее выделяем нужный размер и заполняем через set().
            int totalVertices = totalTriangles * 3;
            VertexList vertexList = new VertexList();
            vertexList.resize(totalVertices);

            try (IndexedTriangleList indexList = new IndexedTriangleList()) {
                indexList.resize(totalTriangles);

                int triangleIndex = 0;
                int vertexIndex = 0;

                for (float[] verts : allVertices) {
                    for (int i = 0; i < verts.length; i += 9) {
                        vertexList.set(vertexIndex, verts[i], verts[i + 1], verts[i + 2]);
                        vertexList.set(vertexIndex + 1, verts[i + 3], verts[i + 4], verts[i + 5]);
                        vertexList.set(vertexIndex + 2, verts[i + 6], verts[i + 7], verts[i + 8]);
                        indexList.set(triangleIndex, vertexIndex, vertexIndex + 1, vertexIndex + 2, 0);

                        vertexIndex += 3;
                        triangleIndex++;
                    }
                }

                try (MeshShapeSettings meshSettings = new MeshShapeSettings(vertexList, indexList);
                     ShapeResult result = meshSettings.create()) {
                    if (result.hasError()) {
                        InertiaLogger.warn("Failed to create MeshShape for chunk " + x + "," + z + ": " + result.getError());
                        continue;
                    }

                    try (ShapeRefC meshShape = result.get();
                         BodyCreationSettings bcs = new BodyCreationSettings()) {
                        bcs.setPosition(bodyPosition);
                        bcs.setMotionType(EMotionType.Static);
                        bcs.setObjectLayer(PhysicsLayers.OBJ_STATIC);
                        bcs.setShape(meshShape);
                        bcs.setFriction(group.friction());
                        bcs.setRestitution(group.restitution());

                        try (Body body = bi.createBody(bcs)) {
                            bi.addBody(body, EActivation.DontActivate);
                            world.registerSystemStaticBody(body.getId());
                            newBodyIds.add(body.getId());
                        }
                    } catch (Exception e) {
                        InertiaLogger.error("Failed to create chunk body at " + x + ", " + z, e);
                    }
                }
            }
        }

        if (!newBodyIds.isEmpty()) {
            chunkBodies.put(key, newBodyIds);
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

    private void executeOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), task);
    }

    private record PendingMesh(int x, int z, GreedyMeshData data, long sequence) {}

    private record MeshGroup(float friction, float restitution, List<float[]> vertices) {
        MeshGroup(float friction, float restitution) {
            this(friction, restitution, new ArrayList<>());
        }
    }

    private record ScoredPendingMesh(PendingMesh pending, double distance) {}
}
