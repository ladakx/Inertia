package com.ladakx.inertia.physics.world.terrain.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
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
import com.ladakx.inertia.physics.world.terrain.ChunkPhysicsManager.GenerationRequestKind;
import com.ladakx.inertia.physics.world.terrain.ChunkSnapshotData;
import com.ladakx.inertia.physics.world.terrain.DirtyChunkRegion;
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
import java.lang.Character;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class GreedyMeshAdapter implements TerrainAdapter {
    private PhysicsWorld world;
    private ChunkPhysicsManager chunkPhysicsManager;
    private JoltTools joltTools;
    private BlocksConfig blocksConfig;
    private GreedyMeshGenerator generator;
    private final Map<Long, Map<Integer, List<Integer>>> chunkBodies = new HashMap<>();
    private final Set<Long> loadedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, BukkitTask> pendingUpdates = new HashMap<>();
    private final Map<Long, PendingMesh> pendingMeshData = new ConcurrentHashMap<>();
    private final Queue<PendingMeshHandoff> pendingMeshHandoffs = new ConcurrentLinkedQueue<>();
    private WorldsConfig.ChunkManagementSettings chunkSettings;
    private WorldsConfig.GreedyMeshShapeType greedyMeshShapeType = WorldsConfig.GreedyMeshShapeType.MESH_SHAPE;
    private boolean useFastChunkCapture = true;
    private int maxCaptureMillisPerTick = 2;
    private int maxCapturePerTick = 4;
    private final AtomicLong meshSequence = new AtomicLong();
    private UUID meshApplyTaskId;
    private BukkitTask captureTickTask;
    private ChunkPhysicsCache cache;

    private static final int TERRAIN_ALGORITHM_VERSION = 1;
    private static final long OFFLINE_GENERATION_AWAIT_TIMEOUT_SECONDS = 30L;

    @Override
    public void onEnable(PhysicsWorld world) {
        this.world = world;
        InertiaConfig config = InertiaPlugin.getInstance().getConfigManager().getInertiaConfig();
        InertiaConfig.TerrainThreadingSettings terrainThreading = config.PERFORMANCE.THREADING.terrain;
        InertiaConfig.PhysicsSettings.ChunkCacheSettings cacheSettings = config.PHYSICS.CHUNK_CACHE;

        this.joltTools = InertiaPlugin.getInstance().getJoltTools();
        this.blocksConfig = InertiaPlugin.getInstance().getConfigManager().getBlocksConfig();
        this.chunkSettings = world.getSettings().chunkManagement();
        WorldsConfig.GreedyMeshingSettings meshingSettings = world.getSettings().simulation().greedyMeshing();
        this.greedyMeshShapeType = meshingSettings.shapeType();
        this.useFastChunkCapture = meshingSettings.fastChunkCapture();
        this.maxCaptureMillisPerTick = Math.max(0, terrainThreading.captureBudgetMs);
        this.maxCapturePerTick = Math.max(1, config.PHYSICS.TERRAIN_GENERATION.maxCapturePerTick);

        GenerationQueue generationQueue = new GenerationQueue(terrainThreading.generateWorkers, terrainThreading.maxInFlight);
        File worldFolder = world.getWorldBukkit().getWorldFolder();
        File cacheDir = new File(worldFolder, "physics");
        Duration memoryCacheTtl = Duration.ofSeconds(Math.max(0, cacheSettings.memoryTtlSeconds));
        Duration diskCacheTtl = Duration.ofSeconds(Math.max(0, cacheSettings.diskTtlSeconds));
        String pluginVersion = InertiaPlugin.getInstance().getDescription().getVersion();
        long worldSeed = world.getWorldBukkit().getSeed();
        String configHash = computeConfigHash();
        this.cache = new ChunkPhysicsCache(
                cacheDir,
                cacheSettings.maxEntries,
                memoryCacheTtl,
                diskCacheTtl,
                pluginVersion,
                worldSeed,
                configHash,
                createCacheMetadata(meshingSettings)
        );

        this.generator = new GreedyMeshGenerator(
                blocksConfig,
                joltTools,
                meshingSettings
        );

        this.chunkPhysicsManager = new ChunkPhysicsManager(generationQueue, cache, generator);
        this.meshApplyTaskId = world.addTickTask(this::processPendingMeshes);
        this.captureTickTask = Bukkit.getScheduler().runTaskTimer(InertiaPlugin.getInstance(), this::processCaptureQueue, 1L, 1L);

        if (chunkSettings.generateOnLoad()) {
            for (Chunk chunk : world.getWorldBukkit().getLoadedChunks()) {
                onChunkLoad(chunk.getX(), chunk.getZ());
            }
        }
    }

    @Override
    public void onDisable() {
        ensureMainThread("onDisable");
        pendingUpdates.values().forEach(BukkitTask::cancel);
        pendingUpdates.clear();
        if (world != null) {
            world.schedulePhysicsTask(this::removeAllChunkBodies);
        }
        if (meshApplyTaskId != null) {
            world.removeTickTask(meshApplyTaskId);
            meshApplyTaskId = null;
        }
        if (captureTickTask != null) {
            captureTickTask.cancel();
            captureTickTask = null;
        }
        if (chunkPhysicsManager != null) {
            chunkPhysicsManager.close();
        }
        loadedChunks.clear();
        pendingMeshData.clear();
        pendingMeshHandoffs.clear();
        chunkPhysicsManager = null;
        cache = null;
        generator = null;
        joltTools = null;
        world = null;
    }

    @Override
    public void onChunkLoad(int x, int z) {
        ensureMainThread("onChunkLoad");
        long key = ChunkUtils.getChunkKey(x, z);
        loadedChunks.add(key);
        if (chunkSettings.generateOnLoad()) {
            requestChunkGeneration(x, z, null, GenerationRequestKind.GENERATE_ON_LOAD);
        }
    }

    @Override
    public void onChunkUnload(int x, int z) {
        ensureMainThread("onChunkUnload");
        long key = ChunkUtils.getChunkKey(x, z);
        loadedChunks.remove(key);
        pendingMeshHandoffs.offer(PendingMeshHandoff.discardChunk(key));

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
        ensureMainThread("onBlockChange");
        if (world == null || chunkPhysicsManager == null || !chunkSettings.updateOnBlockChange()) return;
        if (!hasPhysicalProfile(oldMaterial) && !hasPhysicalProfile(newMaterial)) return;

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = ChunkUtils.getChunkKey(chunkX, chunkZ);
        if (!loadedChunks.contains(key)) return;

        activateDynamicBodiesInChunk(chunkX, chunkZ);
        chunkPhysicsManager.invalidate(chunkX, chunkZ);

        BukkitTask existing = pendingUpdates.get(key);
        if (existing != null) existing.cancel();

        int delay = Math.max(1, chunkSettings.updateDebounceTicks());
        BukkitTask task = Bukkit.getScheduler().runTaskLater(InertiaPlugin.getInstance(), () -> {
            pendingUpdates.remove(key);
            if (loadedChunks.contains(key)) {
                requestChunkGeneration(chunkX, chunkZ, null, GenerationRequestKind.DIRTY);
            }
        }, delay);
        pendingUpdates.put(key, task);
    }

    @Override
    public void onChunkChange(int x, int z) {
        ensureMainThread("onChunkChange");
        if (world == null || chunkPhysicsManager == null) return;
        long key = ChunkUtils.getChunkKey(x, z);
        if (!loadedChunks.contains(key)) return;

        activateDynamicBodiesInChunk(x, z);

        chunkPhysicsManager.invalidate(x, z);
        BukkitTask pending = pendingUpdates.remove(key);
        if (pending != null) pending.cancel();

        requestChunkGeneration(x, z, null, GenerationRequestKind.DIRTY);
    }

    private String computeConfigHash() {
        String source = InertiaPlugin.getInstance().getConfig().saveToString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(source.hashCode());
        }
    }


    @Override
    public CompletableFuture<OfflineGenerationResult> generateOffline(OfflineGenerationRequest request) {
        if (world == null || chunkPhysicsManager == null || generator == null || cache == null) {
            return CompletableFuture.completedFuture(OfflineGenerationResult.unsupported());
        }

        return CompletableFuture.supplyAsync(() -> {
            long started = System.currentTimeMillis();
            Set<Long> targetChunks = collectTargetChunks(request);
            AtomicInteger generated = new AtomicInteger();
            AtomicInteger skipped = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();

            for (long key : targetChunks) {
                int chunkX = ChunkUtils.getChunkX(key);
                int chunkZ = ChunkUtils.getChunkZ(key);

                if (!request.forceRegenerate() && cache.get(chunkX, chunkZ).isPresent()) {
                    skipped.incrementAndGet();
                    continue;
                }

                if (request.forceRegenerate()) {
                    cache.invalidate(chunkX, chunkZ);
                }

                try {
                    ChunkSnapshotData snapshot = requestChunkSnapshotSync(chunkX, chunkZ);
                    if (snapshot == null) {
                        failed.incrementAndGet();
                        continue;
                    }
                    GreedyMeshData meshData = generator.generate(snapshot, null);
                    cache.put(chunkX, chunkZ, new CachedChunkPhysicsData(meshData, snapshot.sectionFingerprints()));
                    generated.incrementAndGet();
                } catch (Exception ex) {
                    failed.incrementAndGet();
                    InertiaLogger.warn("Offline terrain generation failed for chunk " + chunkX + ", " + chunkZ + " in world " + world.getWorldBukkit().getName(), ex);
                }
            }

            long elapsed = Math.max(0L, System.currentTimeMillis() - started);
            return new OfflineGenerationResult(
                    true,
                    targetChunks.size(),
                    generated.get(),
                    skipped.get(),
                    failed.get(),
                    elapsed
            );
        });
    }

    private Set<Long> collectTargetChunks(OfflineGenerationRequest request) {
        Set<Long> chunks = new LinkedHashSet<>();
        if (request.useWorldBounds()) {
            var size = world.getSettings().size();
            int minChunkX = ((int) Math.floor(size.worldMin().xx())) >> 4;
            int maxChunkX = ((int) Math.floor(size.worldMax().xx())) >> 4;
            int minChunkZ = ((int) Math.floor(size.worldMin().zz())) >> 4;
            int maxChunkZ = ((int) Math.floor(size.worldMax().zz())) >> 4;
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    chunks.add(ChunkUtils.getChunkKey(x, z));
                }
            }
            return chunks;
        }

        int radius = Math.max(0, request.radiusChunks());
        for (int x = request.centerChunkX() - radius; x <= request.centerChunkX() + radius; x++) {
            for (int z = request.centerChunkZ() - radius; z <= request.centerChunkZ() + radius; z++) {
                chunks.add(ChunkUtils.getChunkKey(x, z));
            }
        }
        return chunks;
    }

    private ChunkSnapshotData requestChunkSnapshotSync(int chunkX, int chunkZ) {
        CompletableFuture<ChunkSnapshotData> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
            try {
                Chunk chunk = world.getWorldBukkit().getChunkAt(chunkX, chunkZ);
                boolean loaded = world.getWorldBukkit().isChunkLoaded(chunkX, chunkZ);
                if (!loaded) {
                    chunk.load(true);
                }
                future.complete(captureChunkSnapshotData(chunkX, chunkZ));
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        try {
            return future.get(OFFLINE_GENERATION_AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CompletionException(ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new CompletionException(ex);
        }
    }

    private ChunkPhysicsCache.CacheGenerationMetadata createCacheMetadata(WorldsConfig.GreedyMeshingSettings meshingSettings) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("shapeType", String.valueOf(greedyMeshShapeType));
        parameters.put("fastChunkCapture", String.valueOf(useFastChunkCapture));
        parameters.put("verticalMerging", String.valueOf(meshingSettings.verticalMerging()));
        parameters.put("maxVerticalSize", String.valueOf(meshingSettings.maxVerticalSize()));
        parameters.put("maxCaptureMillisPerTick", String.valueOf(meshingSettings.maxCaptureMillisPerTick()));
        return ChunkPhysicsCache.CacheGenerationMetadata.of(greedyMeshShapeType.name(), TERRAIN_ALGORITHM_VERSION, parameters);
    }

    private void requestChunkGeneration(int x, int z) {
        requestChunkGeneration(x, z, null, GenerationRequestKind.GENERATE_ON_LOAD);
    }

    private void requestChunkGeneration(int x, int z, DirtyChunkRegion dirtyRegion) {
        requestChunkGeneration(x, z, dirtyRegion, dirtyRegion == null ? GenerationRequestKind.GENERATE_ON_LOAD : GenerationRequestKind.DIRTY);
    }

    private void requestChunkGeneration(int x, int z, DirtyChunkRegion dirtyRegion, GenerationRequestKind requestKind) {
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
                () -> captureChunkSnapshotData(x, z),
                data -> {
                    if (world != null) {
                        pendingMeshHandoffs.offer(PendingMeshHandoff.meshData(x, z, data, meshSequence.incrementAndGet()));
                    }
                },
                dirtyRegion,
                requestKind
        );
    }

    private void processCaptureQueue() {
        if (world == null || chunkPhysicsManager == null) {
            return;
        }
        List<ChunkPhysicsManager.ChunkCoordinate> playerChunks = world.getWorldBukkit().getPlayers().stream()
                .map(player -> new ChunkPhysicsManager.ChunkCoordinate(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4))
                .toList();
        chunkPhysicsManager.processCaptureQueue(playerChunks, maxCapturePerTick, maxCaptureMillisPerTick);
    }


    public ChunkPhysicsManager.CaptureMetrics getCaptureMetrics() {
        if (chunkPhysicsManager == null) {
            return new ChunkPhysicsManager.CaptureMetrics(0, 0.0, 0.0, 0.0, 0, 0, 0);
        }
        return chunkPhysicsManager.getCaptureMetrics();
    }

    private ChunkSnapshotData captureChunkSnapshotData(int x, int z) {
        Chunk chunk = world.getWorldBukkit().getChunkAt(x, z);
//        long startedNanos = System.nanoTime();
        short[] profileMap = generator != null ? generator.materialToProfileId() : null;
        ChunkSnapshotData snapshotData = useFastChunkCapture
                ? ChunkSnapshotData.captureFast(chunk, joltTools, profileMap)
                : ChunkSnapshotData.capture(chunk, joltTools, profileMap);
//        long elapsedNanos = System.nanoTime() - startedNanos;
//        double elapsedMillis = elapsedNanos / 1_000_000.0;
//        InertiaLogger.debug("Chunk capture [" + (useFastChunkCapture ? "fast" : "legacy") + "] "
//                + world.getWorldBukkit().getName() + " (" + x + "," + z + "): "
//                + elapsedNanos + "ns (" + String.format(java.util.Locale.ROOT, "%.3f", elapsedMillis) + "ms)");
        return snapshotData;
    }

    private void activateDynamicBodiesInChunk(int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }

        world.schedulePhysicsTask(() -> activateDynamicBodiesNearChunk(chunkX, chunkZ, 1));
    }

    private void activateDynamicBodiesNearChunk(int centerChunkX, int centerChunkZ, int rangeChunks) {
        if (world == null) {
            return;
        }

        for (InertiaPhysicsBody body : world.getBodies()) {
            if (!(body instanceof AbstractPhysicsBody physicsBody)) {
                continue;
            }
            if (!physicsBody.isValid() || physicsBody.getMotionType() != MotionType.DYNAMIC) {
                continue;
            }

            RVec3 position = physicsBody.getBody().getPosition();
            int bodyChunkX = ((int) Math.floor(position.xx() + world.getOrigin().xx())) >> 4;
            int bodyChunkZ = ((int) Math.floor(position.zz() + world.getOrigin().zz())) >> 4;

            if (Math.abs(bodyChunkX - centerChunkX) <= rangeChunks && Math.abs(bodyChunkZ - centerChunkZ) <= rangeChunks) {
                physicsBody.activate();
            }
        }
    }

    private void processPendingMeshes() {
        if (world == null) {
            return;
        }

        drainMeshHandoffs();

        if (pendingMeshData.isEmpty()) return;

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

    private void drainMeshHandoffs() {
        PendingMeshHandoff handoff;
        while ((handoff = pendingMeshHandoffs.poll()) != null) {
            if (handoff.discardChunk()) {
                pendingMeshData.remove(handoff.chunkKey());
                continue;
            }
            long key = ChunkUtils.getChunkKey(handoff.x(), handoff.z());
            pendingMeshData.put(key, new PendingMesh(handoff.x(), handoff.z(), handoff.data(), handoff.sequence()));
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
     * Группирует примитивы по физическим свойствам и создает тело чанка
     * через MeshShape или CompoundShape в зависимости от конфигурации.
     */
    private void applyMeshData(int x, int z, GreedyMeshData data) {
        long key = ChunkUtils.getChunkKey(x, z);
        if (!loadedChunks.contains(key) || world == null) return;

        BodyInterface bi = world.getBodyInterface();
        RVec3 worldOrigin = world.getOrigin();

        double chunkWorldX = x * 16.0 - worldOrigin.xx();
        double chunkWorldZ = z * 16.0 - worldOrigin.zz();
        double chunkWorldY = -worldOrigin.yy();

        RVec3 bodyPosition = new RVec3(chunkWorldX, chunkWorldY, chunkWorldZ);

        Map<String, MeshGroup> groupedVertices = new HashMap<>();

        for (GreedyMeshShape shapeData : data.shapes()) {
            if (shapeData.vertices().length == 0) continue;
            int sectionY = shapeData.minY() >> 4;
            String groupKey = shapeData.materialId() + '#' + sectionY;
            MeshGroup group = groupedVertices.computeIfAbsent(
                    groupKey,
                    materialId -> new MeshGroup(sectionY, shapeData.friction(), shapeData.restitution())
            );
            group.vertices().add(shapeData.vertices());
            group.shapes().add(shapeData);
        }

        // Build new section bodies first, then remove old ones and swap.
        // This avoids a temporary "no collision" gap for the chunk while it is being replaced.
        Map<Integer, List<Integer>> builtSectionBodies = new HashMap<>();
        for (MeshGroup group : groupedVertices.values()) {
            List<Integer> newBodyIds = new ArrayList<>();
            if (greedyMeshShapeType == WorldsConfig.GreedyMeshShapeType.COMPOUND_SHAPE) {
                createCompoundBodyForGroup(x, z, bi, bodyPosition, group, newBodyIds);
            } else {
                createMeshBodyForGroup(x, z, bi, bodyPosition, group, newBodyIds);
            }
            if (!newBodyIds.isEmpty()) {
                builtSectionBodies.computeIfAbsent(group.sectionY(), unused -> new ArrayList<>()).addAll(newBodyIds);
            }
        }

        // Wake up nearby dynamic bodies right before and after terrain-body swap,
        // so contact pairs rebuild immediately.
        activateDynamicBodiesNearChunk(x, z, 1);

        if (data.fullRebuild()) {
            removeChunkBodies(key);
        } else {
            removeChunkBodies(key, data.touchedSections());
        }

        Map<Integer, List<Integer>> sectionBodies = chunkBodies.computeIfAbsent(key, unused -> new HashMap<>());
        if (data.fullRebuild()) {
            sectionBodies.clear();
        }

        for (Map.Entry<Integer, List<Integer>> entry : builtSectionBodies.entrySet()) {
            sectionBodies.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>()).addAll(entry.getValue());
        }

        if (sectionBodies.isEmpty()) {
            chunkBodies.remove(key);
        }

        activateDynamicBodiesNearChunk(x, z, 1);
    }

    private void createMeshBodyForGroup(int x, int z,
                                        BodyInterface bi,
                                        RVec3 bodyPosition,
                                        MeshGroup group,
                                        List<Integer> newBodyIds) {
        List<float[]> allVertices = group.vertices();

        int totalTriangles = 0;
        for (float[] verts : allVertices) {
            totalTriangles += verts.length / 9;
        }

        if (totalTriangles == 0) return;

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
                    try (IndexedTriangle tri = new IndexedTriangle(vertexIndex, vertexIndex + 1, vertexIndex + 2, 0)) {
                        indexList.set(triangleIndex, tri);
                    }

                    vertexIndex += 3;
                    triangleIndex++;
                }
            }

            try (MeshShapeSettings meshSettings = new MeshShapeSettings(vertexList, indexList);
                 ShapeResult result = meshSettings.create()) {
                if (result.hasError()) {
                    InertiaLogger.warn("Failed to create MeshShape for chunk " + x + "," + z + ": " + result.getError());
                    return;
                }

                try (ShapeRefC meshShape = result.get()) {
                    createStaticBody(bi, bodyPosition, group, meshShape, x, z, newBodyIds);
                }
            }
        }
    }

    private void createCompoundBodyForGroup(int x, int z,
                                            BodyInterface bi,
                                            RVec3 bodyPosition,
                                            MeshGroup group,
                                            List<Integer> newBodyIds) {
        if (group.shapes().isEmpty()) return;

        StaticCompoundShapeSettings compoundSettings = new StaticCompoundShapeSettings();
        Quat identity = new Quat(0f, 0f, 0f, 1f);

        for (GreedyMeshShape shapeData : group.shapes()) {
            float sizeX = shapeData.maxX() - shapeData.minX();
            float sizeY = shapeData.maxY() - shapeData.minY();
            float sizeZ = shapeData.maxZ() - shapeData.minZ();
            if (sizeX <= 0f || sizeY <= 0f || sizeZ <= 0f) {
                continue;
            }

            Vec3 halfExtents = new Vec3(sizeX * 0.5f, sizeY * 0.5f, sizeZ * 0.5f);
            Vec3 center = new Vec3(
                    shapeData.minX() + halfExtents.getX(),
                    shapeData.minY() + halfExtents.getY(),
                    shapeData.minZ() + halfExtents.getZ()
            );

            compoundSettings.addShape(center, identity, new BoxShape(halfExtents));
        }

        try (ShapeResult result = compoundSettings.create()) {
            if (result.hasError()) {
                InertiaLogger.warn("Failed to create CompoundShape for chunk " + x + "," + z + ": " + result.getError());
                return;
            }

            try (ShapeRefC compoundShape = result.get()) {
                createStaticBody(bi, bodyPosition, group, compoundShape, x, z, newBodyIds);
            }
        }
    }

    private void createStaticBody(BodyInterface bi,
                                  RVec3 bodyPosition,
                                  MeshGroup group,
                                  ShapeRefC shape,
                                  int chunkX,
                                  int chunkZ,
                                  List<Integer> newBodyIds) {
        try (BodyCreationSettings bcs = new BodyCreationSettings()) {
            bcs.setPosition(bodyPosition);
            bcs.setMotionType(EMotionType.Static);
            bcs.setObjectLayer(PhysicsLayers.OBJ_STATIC);
            bcs.setShape(shape);
            bcs.setFriction(group.friction());
            bcs.setRestitution(group.restitution());

            try (Body body = bi.createBody(bcs)) {
                bi.addBody(body, EActivation.DontActivate);
                world.registerSystemStaticBody(body.getId());
                newBodyIds.add(body.getId());
            }
        } catch (Exception e) {
            InertiaLogger.error("Failed to create chunk body at " + chunkX + ", " + chunkZ, e);
        }
    }

    private void removeChunkBodies(long key) {
        if (world == null) return;
        Map<Integer, List<Integer>> sectionBodies = chunkBodies.remove(key);
        if (sectionBodies == null || sectionBodies.isEmpty()) return;

        BodyInterface bi = world.getBodyInterface();
        for (List<Integer> ids : sectionBodies.values()) {
            removeBodyIds(bi, ids);
        }
    }

    private void removeAllChunkBodies() {
        if (world == null) return;
        for (long key : new ArrayList<>(chunkBodies.keySet())) {
            removeChunkBodies(key);
        }
    }

    private void ensureMainThread(String methodName) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(methodName + " must be called on the primary Bukkit thread");
        }
    }

    private void removeChunkBodies(long key, Set<Integer> sections) {
        if (world == null || sections == null || sections.isEmpty()) return;

        Map<Integer, List<Integer>> sectionBodies = chunkBodies.get(key);
        if (sectionBodies == null || sectionBodies.isEmpty()) return;

        BodyInterface bi = world.getBodyInterface();
        for (Integer section : sections) {
            List<Integer> ids = sectionBodies.remove(section);
            if (ids != null) {
                removeBodyIds(bi, ids);
            }
        }

        if (sectionBodies.isEmpty()) {
            chunkBodies.remove(key);
        }
    }

    private void removeBodyIds(BodyInterface bi, List<Integer> ids) {
        for (int id : ids) {
            world.unregisterSystemStaticBody(id);
            try {
                bi.removeBody(id);
                bi.destroyBody(id);
            } catch (Exception ignored) {}
        }
    }

    private record PendingMesh(int x, int z, GreedyMeshData data, long sequence) {}

    private record PendingMeshHandoff(int x, int z, long chunkKey, GreedyMeshData data, long sequence, boolean discardChunk) {
        private static PendingMeshHandoff meshData(int x, int z, GreedyMeshData data, long sequence) {
            return new PendingMeshHandoff(x, z, 0L, data, sequence, false);
        }

        private static PendingMeshHandoff discardChunk(long chunkKey) {
            return new PendingMeshHandoff(0, 0, chunkKey, null, 0L, true);
        }
    }

    private record MeshGroup(int sectionY, float friction, float restitution, List<float[]> vertices, List<GreedyMeshShape> shapes) {
        MeshGroup(int sectionY, float friction, float restitution) {
            this(sectionY, friction, restitution, new ArrayList<>(), new ArrayList<>());
        }
    }

    private record ScoredPendingMesh(PendingMesh pending, double distance) {}
}
