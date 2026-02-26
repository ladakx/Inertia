package com.ladakx.inertia.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.InertiaApiAccess;
import com.ladakx.inertia.api.capability.ApiCapabilities;
import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.api.interaction.PhysicsInteraction;
import com.ladakx.inertia.api.interaction.RaycastHit;
import com.ladakx.inertia.api.physics.PhysicsBodySpec;
import com.ladakx.inertia.api.service.PhysicsMetricsService;
import com.ladakx.inertia.common.chunk.ChunkTicketManager;
import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.events.PhysicsBodyPostSpawnEvent;
import com.ladakx.inertia.api.events.PhysicsBodyPreSpawnEvent;
import com.ladakx.inertia.api.events.PhysicsWorldPauseChangedEvent;
import com.ladakx.inertia.api.events.PhysicsWorldPauseChangedPayload;
import com.ladakx.inertia.api.diagnostics.DiagnosticsService;
import com.ladakx.inertia.api.events.PhysicsBackpressureEvent;
import com.ladakx.inertia.api.events.PhysicsBackpressurePayload;
import com.ladakx.inertia.api.events.PhysicsWorldOverloadEvent;
import com.ladakx.inertia.api.events.PhysicsWorldOverloadPayload;
import com.ladakx.inertia.api.events.PhysicsWorldTickEndEvent;
import com.ladakx.inertia.api.events.PhysicsWorldTickPayload;
import com.ladakx.inertia.api.events.PhysicsWorldTickStartEvent;
import com.ladakx.inertia.api.events.PhysicsEventPayload;
import com.ladakx.inertia.physics.body.impl.CustomPhysicsBody;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody;
import com.ladakx.inertia.physics.world.loop.LoopDiagnosticsSnapshot;
import com.ladakx.inertia.physics.world.loop.LoopTickListener;
import com.ladakx.inertia.physics.world.loop.PhysicsLoop;
import com.ladakx.inertia.physics.entity.EntityPhysicsManager;
import com.ladakx.inertia.physics.events.BukkitEventPublisher;
import com.ladakx.inertia.physics.events.BukkitMainThreadExecutor;
import com.ladakx.inertia.physics.events.PhysicsEventDispatcher;
import com.ladakx.inertia.physics.world.managers.*;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import com.ladakx.inertia.physics.world.snapshot.SnapshotPool;
import com.ladakx.inertia.physics.world.snapshot.VisualState;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.physics.world.buoyancy.BuoyancyManager;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import com.ladakx.inertia.common.utils.ConvertUtils;
import com.ladakx.inertia.physics.factory.shape.ApiShapeConverter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PhysicsWorld implements AutoCloseable, com.ladakx.inertia.api.world.PhysicsWorld {

    private final String worldName;
    private final World worldBukkit;
    private final WorldsConfig.WorldProfile settings;
    private final RVec3 origin;

    private final PhysicsSystem physicsSystem;
    private final JobSystem jobSystem;
    private final TempAllocator tempAllocator;
    private final PhysicsLoop physicsLoop;

    private final PhysicsObjectManager objectManager;
    private final PhysicsTaskManager taskManager;
    private final PhysicsQueryEngine queryEngine;
    private final ChunkTicketManager chunkTicketManager;
    private final TerrainAdapter terrainAdapter;

    private final PhysicsContactListener contactListener;
    private final EntityPhysicsManager entityPhysicsManager;
    private final WorldBoundaryManager boundaryManager;

    private final List<Body> staticBodies = new ArrayList<>();
    private final Set<Integer> systemStaticBodyIds = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final SnapshotPool snapshotPool;
    private final InertiaBodyActivationListener bodyActivationListener;
    private final NetworkEntityTracker networkEntityTracker;
    private final @Nullable PhysicsMetricsService metricsService;
    private final @Nullable DiagnosticsService diagnosticsService;
    private final BuoyancyManager buoyancyManager;
    private final ThreadLocal<ByteBuffer> batchTransformBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(7 * Float.BYTES).order(ByteOrder.nativeOrder()));
    private volatile boolean fluidPhysicsEnabled;
    private @Nullable BukkitTask buoyancyScanTask;
    private final ApiShapeConverter apiShapeConverter = new ApiShapeConverter();
    private final PhysicsEventDispatcher eventDispatcher;

    // Removed: private @Nullable BukkitTask networkTickTask;

    public PhysicsWorld(World world,
                        WorldsConfig.WorldProfile settings,
                        PhysicsSystem physicsSystem,
                        JobSystem jobSystem,
                        TempAllocator tempAllocator,
                        TerrainAdapter terrainAdapter,
                        PhysicsMetricsService metricsService,
                        DiagnosticsService diagnosticsService) {
        this.worldBukkit = world;
        this.worldName = world.getName();
        this.settings = settings;
        this.origin = settings.size().origin();

        this.physicsSystem = physicsSystem;
        this.jobSystem = jobSystem;
        this.tempAllocator = tempAllocator;
        this.terrainAdapter = terrainAdapter;

        this.chunkTicketManager = new ChunkTicketManager(world);
        this.objectManager = new PhysicsObjectManager();
        this.taskManager = new PhysicsTaskManager();
        var inertiaConfig = InertiaPlugin.getInstance().getConfigManager().getInertiaConfig();
        var taskManagerSettings = inertiaConfig.PHYSICS.TASK_MANAGER;
        int taskBudgetMs = inertiaConfig.PERFORMANCE.THREADING.physics.taskBudgetMs;
        long totalTaskBudgetNanos = Math.max(100_000L, taskBudgetMs * 1_000_000L);
        long oneTimeBudgetNanos = Math.max(taskManagerSettings.oneTimeTaskBudgetNanos, (long) (totalTaskBudgetNanos * 0.6d));
        long recurringBudgetNanos = Math.max(taskManagerSettings.recurringTaskBudgetNanos, totalTaskBudgetNanos - oneTimeBudgetNanos);
        this.taskManager.updateLimits(
                taskManagerSettings.maxOneTimeTasksPerTick,
                oneTimeBudgetNanos,
                recurringBudgetNanos
        );
        this.snapshotPool = new SnapshotPool();

        this.queryEngine = new PhysicsQueryEngine(this, physicsSystem, objectManager);
        this.networkEntityTracker = InertiaPlugin.getInstance().getNetworkEntityTracker();
        this.buoyancyManager = new BuoyancyManager(this);
        this.fluidPhysicsEnabled = inertiaConfig.PHYSICS.FLUIDS.enabled;

        this.entityPhysicsManager = new EntityPhysicsManager(this, taskManager);
        this.eventDispatcher = new PhysicsEventDispatcher(
                new BukkitMainThreadExecutor(InertiaPlugin.getInstance()),
                new BukkitEventPublisher()
        );
        this.contactListener = new PhysicsContactListener(objectManager, physicsSystem, entityPhysicsManager, eventDispatcher);
        this.physicsSystem.setContactListener(contactListener);

        this.bodyActivationListener = new InertiaBodyActivationListener(objectManager);
        this.physicsSystem.setBodyActivationListener(bodyActivationListener);

        this.boundaryManager = new WorldBoundaryManager(this, settings.size());
        this.boundaryManager.createBoundaries();

        if (this.terrainAdapter != null) {
            this.terrainAdapter.onEnable(this);
        }

        this.physicsSystem.optimizeBroadPhase();

        this.physicsLoop = new PhysicsLoop(
                worldBukkit.getUID(),
                worldName,
                settings.tickRate(),
                settings.performance().maxBodies(),
                this::runPhysicsStep,
                this::collectSnapshot,
                this::applySnapshot,
                () -> physicsSystem.getNumActiveBodies(EBodyType.RigidBody),
                physicsSystem::getNumBodies,
                this::countStaticBodies,
                this.snapshotPool,
                InertiaPlugin.getInstance().getConfigManager().getInertiaConfig().PERFORMANCE.THREADING.physics.snapshotQueueMode
        );

        this.metricsService = metricsService;
        if (metricsService != null) {
            this.physicsLoop.addListener(metricsService);
        }
        this.diagnosticsService = diagnosticsService;
        if (diagnosticsService instanceof LoopTickListener loopTickListener) {
            this.physicsLoop.addListener(loopTickListener);
        }
        this.physicsLoop.addListener(new LoopTickListener() {
            @Override
            public void onTickStart(long tickNumber) {
            }

            @Override
            public void onTickEnd(long tickNumber, long durationNanos, int activeBodies, int totalBodies, int staticBodies, int maxBodies, long droppedSnapshots, long overwrittenSnapshots) {
            }

            @Override
            public void onDiagnostics(LoopDiagnosticsSnapshot snapshot) {
                if (snapshot.backlog()) {
                    PhysicsBackpressurePayload payload = new PhysicsBackpressurePayload(
                            PhysicsEventPayload.SCHEMA_VERSION_V1,
                            snapshot.worldId(),
                            snapshot.worldName(),
                            snapshot.pendingSnapshots(),
                            snapshot.droppedSnapshots(),
                            snapshot.overwrittenSnapshots(),
                            snapshot.backlogTicks()
                    );
                    eventDispatcher.dispatchAsync(new PhysicsBackpressureEvent(payload), payload);
                }
                if (snapshot.overloaded()) {
                    PhysicsWorldOverloadPayload payload = new PhysicsWorldOverloadPayload(
                            PhysicsEventPayload.SCHEMA_VERSION_V1,
                            snapshot.worldId(),
                            snapshot.worldName(),
                            snapshot.tickNumber(),
                            snapshot.durationNanos() / 1_000_000.0d,
                            snapshot.configuredTps(),
                            snapshot.overloadedTicks()
                    );
                    eventDispatcher.dispatchAsync(new PhysicsWorldOverloadEvent(payload), payload);
                }
            }
        });

        updateBuoyancyTask();

        // Removed: networkTickTask scheduling.
        // The global tracker tick is now handled by InertiaPlugin main class.
    }

    private void runPhysicsStep() {
        PhysicsWorldTickPayload tickPayload = new PhysicsWorldTickPayload(
                PhysicsEventPayload.SCHEMA_VERSION_V1,
                worldBukkit.getUID(),
                worldName,
                isPaused.get(),
                settings.tickRate()
        );
        eventDispatcher.dispatchAsync(new PhysicsWorldTickStartEvent(tickPayload), tickPayload);
        if (isPaused.get()) {
            eventDispatcher.dispatchAsync(new PhysicsWorldTickEndEvent(tickPayload), tickPayload);
            return;
        }
        float deltaTime = 1.0f / settings.tickRate();
        if (fluidPhysicsEnabled) {
            buoyancyManager.applyBuoyancyForces(deltaTime);
        }
        int errors = physicsSystem.update(deltaTime, settings.collisionSteps(), tempAllocator, jobSystem);
        if (errors != EPhysicsUpdateError.None) {
            InertiaLogger.warn("Physics error in world " + worldName + ": " + errors);
        }
        PhysicsTaskManager.TaskManagerMetrics taskManagerMetrics = taskManager.runAll();
        if (metricsService != null) {
            metricsService.updateTaskManagerMetrics(taskManagerMetrics);
        }
        eventDispatcher.dispatchAsync(new PhysicsWorldTickEndEvent(tickPayload), tickPayload);
    }

    public boolean isFluidPhysicsEnabled() {
        return fluidPhysicsEnabled;
    }

    public void setFluidPhysicsEnabled(boolean enabled) {
        if (this.fluidPhysicsEnabled == enabled) {
            return;
        }
        this.fluidPhysicsEnabled = enabled;
        updateBuoyancyTask();
    }

    private void updateBuoyancyTask() {
        if (!fluidPhysicsEnabled) {
            if (buoyancyScanTask != null) {
                buoyancyScanTask.cancel();
                buoyancyScanTask = null;
            }
            return;
        }

        if (buoyancyScanTask != null) {
            return;
        }

        InertiaPlugin plugin = InertiaPlugin.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        buoyancyScanTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () ->
                buoyancyManager.updateFluidStates(objectManager.getActive()), 1L, 1L);
    }

    private PhysicsSnapshot collectSnapshot() {
        Collection<AbstractPhysicsBody> allBodies = objectManager.getAll();
        List<VisualState> updates = snapshotPool.borrowList();
        java.util.Set<Long> bodiesChunkKeys = new java.util.HashSet<>();
        List<AbstractPhysicsBody> toDestroy = new ArrayList<>();

        BodyInterface bodyInterface = physicsSystem.getBodyInterfaceNoLock();
        WorldsConfig.WorldSizeSettings sizeSettings = settings.size();

        List<DisplayedPhysicsBody> batchActiveBodies = new ArrayList<>();
        List<Long> batchActiveBodyIds = new ArrayList<>();

        for (AbstractPhysicsBody obj : allBodies) {
            if (!obj.isValid()) {
                continue;
            }

            int bodyId = obj.getBody().getId();
            RVec3 joltPos = bodyInterface.getCenterOfMassPosition(bodyId);

            if (sizeSettings.killBelowMinY() && boundaryManager.isBelowBottom(joltPos)) {
                toDestroy.add(obj);
                continue;
            }

            if (sizeSettings.preventExit() && !boundaryManager.isInside(joltPos)) {
                toDestroy.add(obj);
                continue;
            }

            double worldX = joltPos.xx() + origin.xx();
            double worldZ = joltPos.zz() + origin.zz();
            int chunkX = (int) Math.floor(worldX) >> 4;
            int chunkZ = (int) Math.floor(worldZ) >> 4;
            bodiesChunkKeys.add(ChunkUtils.getChunkKey(chunkX, chunkZ));

            if (obj instanceof DisplayedPhysicsBody displayed) {
                if (!displayed.isDisplayCaptureRequired()) {
                    continue;
                }
                if (displayed.getBody().isActive()) {
                    batchActiveBodies.add(displayed);
                    batchActiveBodyIds.add((long) bodyId);
                } else {
                    displayed.captureSnapshotSleeping(updates, snapshotPool, origin);
                }
            }
        }

        captureDisplayBatch(updates, batchActiveBodies, batchActiveBodyIds);
        return new PhysicsSnapshot(updates, bodiesChunkKeys, toDestroy);
    }

    private void captureDisplayBatch(List<VisualState> updates,
                                     List<DisplayedPhysicsBody> batchBodies,
                                     List<Long> batchBodyIds) {
        if (batchBodies.isEmpty()) {
            return;
        }

        ByteBuffer buffer = ensureBatchBuffer(batchBodies.size());
        java.nio.FloatBuffer floatBuffer = buffer.asFloatBuffer();
        long[] bodyIds = new long[batchBodyIds.size()];
        for (int i = 0; i < batchBodyIds.size(); ++i) {
            bodyIds[i] = batchBodyIds.get(i);
        }

        int activeCount = Body.getBatchTransforms(physicsSystem.getBodyInterfaceNoLock().va(), bodyIds, bodyIds.length, buffer);
        for (int i = 0; i < activeCount; ++i) {
            int offset = i * 7;
            batchBodies.get(i).captureSnapshotActive(
                    updates,
                    snapshotPool,
                    origin,
                    floatBuffer.get(offset),
                    floatBuffer.get(offset + 1),
                    floatBuffer.get(offset + 2),
                    floatBuffer.get(offset + 3),
                    floatBuffer.get(offset + 4),
                    floatBuffer.get(offset + 5),
                    floatBuffer.get(offset + 6)
            );
        }

    }

    private ByteBuffer ensureBatchBuffer(int bodyCount) {
        int neededFloats = Math.max(1, bodyCount * 7);
        ByteBuffer buffer = batchTransformBuffer.get();
        if (buffer.capacity() < neededFloats * Float.BYTES) {
            buffer = ByteBuffer.allocateDirect(neededFloats * Float.BYTES).order(ByteOrder.nativeOrder());
            batchTransformBuffer.set(buffer);
        }
        buffer.clear();
        buffer.limit(neededFloats * Float.BYTES);
        return buffer;
    }

    private void applySnapshot(PhysicsSnapshot snapshot) {
        entityPhysicsManager.syncFromBukkit();
        entityPhysicsManager.drainFeedback();
        for (AbstractPhysicsBody body : snapshot.bodiesToDestroy()) {
            if (body.isValid()) {
                schedulePhysicsTask(body::destroy);
            }
        }

        chunkTicketManager.updateTickets(snapshot.activeChunkKeys());

        if (snapshot.updates() != null) {
            Location mutableLoc = new Location(worldBukkit, 0, 0, 0);
            for (VisualState state : snapshot.updates()) {
                if (state.getVisual() == null) {
                    continue;
                }

                int visualId = state.getVisual().getId();
                AbstractPhysicsBody owner = getObjectByNetworkEntityId(visualId);
                if (owner == null || !owner.isValid() || owner.getBody() == null || networkEntityTracker.isVisualClosed(visualId)) {
                    continue;
                }

                mutableLoc.setX(state.getPosition().x);
                mutableLoc.setY(state.getPosition().y);
                mutableLoc.setZ(state.getPosition().z);

                // This only updates the internal state of the tracker (Zero-Allocation / Zero-IO)
                // The actual packets will be formed and sent in the global Flush phase.
                networkEntityTracker.updateStateFromPhysics(
                        state.getVisual(),
                        mutableLoc,
                        state.getRotation(),
                        state.isEnabled()
                );
            }
        }
        snapshot.release(snapshotPool);
    }

    public boolean checkOverlap(@NotNull com.github.stephengold.joltjni.readonly.ConstShape shape,
                                @NotNull RVec3 position,
                                @NotNull Quat rotation) {
        CollideShapeSettings settings = new CollideShapeSettings();
        settings.setActiveEdgeMode(EActiveEdgeMode.CollideOnlyWithActive);

        try (AnyHitCollideShapeCollector collector = new AnyHitCollideShapeCollector();
             SpecifiedBroadPhaseLayerFilter bpFilter = new SpecifiedBroadPhaseLayerFilter(0);
             SpecifiedObjectLayerFilter objFilter = new SpecifiedObjectLayerFilter(PhysicsLayers.OBJ_STATIC)) {

            physicsSystem.getNarrowPhaseQuery().collideShape(
                    shape,
                    Vec3.sReplicate(1.0f),
                    RMat44.sRotationTranslation(rotation, position),
                    settings,
                    RVec3.sZero(),
                    collector,
                    bpFilter,
                    objFilter
            );

            return collector.hadHit();
        }
    }

    // ... (rest of the methods: getters, toJolt, toBukkit, tasks, etc. remain unchanged)

    public RVec3 toJolt(Location location) {
        return new RVec3(
                location.getX() - origin.xx(),
                location.getY() - origin.yy(),
                location.getZ() - origin.zz()
        );
    }

    public Location toBukkit(RVec3 joltVec) {
        return new Location(worldBukkit,
                joltVec.xx() + origin.xx(),
                joltVec.yy() + origin.yy(),
                joltVec.zz() + origin.zz()
        );
    }

    public Vector toBukkitVec(RVec3 joltVec) {
        return new Vector(
                joltVec.xx() + origin.xx(),
                joltVec.yy() + origin.yy(),
                joltVec.zz() + origin.zz()
        );
    }

    public RVec3 getOrigin() { return origin; }
    public boolean isInsideWorld(Location location) {
        if (!location.getWorld().getName().equals(worldName)) return false;
        return boundaryManager.isInside(toJolt(location));
    }
    public WorldBoundaryManager getBoundaryManager() { return boundaryManager; }

    public void createExplosion(@NotNull com.github.stephengold.joltjni.Vec3 originLocal, float force, float radius) {
        Location loc = new Location(worldBukkit, originLocal.getX() + origin.xx(), originLocal.getY() + origin.yy(), originLocal.getZ() + origin.zz());
        queryEngine.createExplosion(loc, force, radius);
    }

    public List<RaycastResult> raycastEntity(@NotNull Location start, @NotNull Vector dir, double dist) {
        // Tools need the internal VA to resolve the hit back to an AbstractPhysicsBody via objectManager.
        // The public API raycast returns an API PhysicsBody wrapper, so we do the narrow-phase query here.
        RVec3 startVec = toJolt(start);
        Vector scaledDir = dir.clone().normalize().multiply(dist);
        Vec3 dirVec = new Vec3((float) scaledDir.getX(), (float) scaledDir.getY(), (float) scaledDir.getZ());

        RRayCast ray = new RRayCast(startVec, dirVec);
        AllHitCastRayCollector collector = new AllHitCastRayCollector();
        physicsSystem.getNarrowPhaseQuery().castRay(ray, new RayCastSettings(), collector);

        List<RayCastResult> hits = collector.getHits();
        if (hits.isEmpty()) {
            return Collections.emptyList();
        }
        hits.sort(java.util.Comparator.comparingDouble(RayCastResult::getFraction));

        var bli = physicsSystem.getBodyLockInterface();
        for (RayCastResult hit : hits) {
            try (BodyLockRead lock = new BodyLockRead(bli, hit.getBodyId())) {
                if (!lock.succeeded()) {
                    continue;
                }
                ConstBody body = lock.getBody();
                long va = body.targetVa();
                if (objectManager.getByVa(va) == null) {
                    continue;
                }

                RVec3 hitPosLocal = ray.getPointOnRay(hit.getFraction());
                return Collections.singletonList(new RaycastResult(va, hitPosLocal));
            }
        }

        return Collections.emptyList();
    }

    public void addObject(AbstractPhysicsBody object) { objectManager.add(object); }
    public void removeObject(AbstractPhysicsBody object) { objectManager.remove(object); }
    public void registerBody(AbstractPhysicsBody object, Body body) { objectManager.registerBody(object, body); }
    public void registerNetworkEntityId(AbstractPhysicsBody object, int entityId) { objectManager.registerNetworkEntityId(object, entityId); }
    public void unregisterNetworkEntityId(int entityId) { objectManager.unregisterNetworkEntityId(entityId); }
    public @Nullable AbstractPhysicsBody getObjectByVa(long va) { return objectManager.getByVa(va); }
    public @Nullable AbstractPhysicsBody getObjectByNetworkEntityId(int entityId) { return objectManager.getByNetworkEntityId(entityId); }
    public @Nullable AbstractPhysicsBody getObjectByUuid(UUID uuid) { return objectManager.getByUuid(uuid); }
    public @NotNull Collection<AbstractPhysicsBody> getObjects() { return objectManager.getAll(); }

    public UUID addTickTask(Runnable runnable) { return taskManager.addTickTask(runnable); }
    public UUID addTickTask(Runnable runnable, PhysicsTaskManager.RecurringTaskPriority priority) {
        return taskManager.addTickTask(runnable, priority);
    }
    public void removeTickTask(UUID uuid) { taskManager.removeTickTask(uuid); }
    public void schedulePhysicsTask(Runnable task) { taskManager.schedule(task); }

    public boolean canSpawnBodies(int amount) { return physicsSystem.getNumBodies() + amount <= settings.performance().maxBodies(); }

    public int getRemainingBodyCapacity() {
        return Math.max(0, settings.performance().maxBodies() - physicsSystem.getNumBodies());
    }

    public void addConstraint(Constraint constraint) {
        physicsSystem.addConstraint(constraint);
        if (constraint instanceof TwoBodyConstraint tbc) {
            linkConstraint(tbc.getBody1().va(), tbc);
            linkConstraint(tbc.getBody2().va(), tbc);
        }
    }

    public void removeConstraint(Constraint constraint) {
        physicsSystem.removeConstraint(constraint);
        if (constraint instanceof TwoBodyConstraint tbc) {
            unlinkConstraint(tbc.getBody1().va(), tbc);
            unlinkConstraint(tbc.getBody2().va(), tbc);
        }
    }

    private void linkConstraint(long bodyVa, TwoBodyConstraint c) {
        AbstractPhysicsBody obj = objectManager.getByVa(bodyVa);
        if (obj != null) obj.addRelatedConstraint(c.toRef());
    }

    private void unlinkConstraint(long bodyVa, TwoBodyConstraint c) {
        AbstractPhysicsBody obj = objectManager.getByVa(bodyVa);
        if (obj != null) obj.removeRelatedConstraint(c.toRef());
    }

    public void onChunkLoad(int x, int z) {
        if (terrainAdapter != null) terrainAdapter.onChunkLoad(x, z);
        for (AbstractPhysicsBody obj : objectManager.getAll()) {
            if (obj instanceof DisplayedPhysicsBody displayed) {
                RVec3 pos = displayed.getBody().getPosition();
                double worldX = pos.xx() + origin.xx();
                double worldZ = pos.zz() + origin.zz();
                if (((int) Math.floor(worldX) >> 4) == x && ((int) Math.floor(worldZ) >> 4) == z) {
                    displayed.checkAndRestoreVisuals();
                }
            }
        }
    }

    public void onChunkUnload(int x, int z) {
        if (terrainAdapter != null) terrainAdapter.onChunkUnload(x, z);
    }

    public void onBlockChange(int x, int y, int z, org.bukkit.Material oldMaterial, org.bukkit.Material newMaterial) {
        if (terrainAdapter != null) terrainAdapter.onBlockChange(x, y, z, oldMaterial, newMaterial);
    }

    public void onChunkChange(int x, int z) {
        if (terrainAdapter != null) terrainAdapter.onChunkChange(x, z);
    }


    public void applyThreadingSettings(com.ladakx.inertia.configuration.dto.InertiaConfig.ThreadingSettings settings) {
        if (settings == null) {
            return;
        }
        long totalTaskBudgetNanos = Math.max(100_000L, settings.physics.taskBudgetMs * 1_000_000L);
        long oneTimeBudgetNanos = Math.max(100_000L, (long) (totalTaskBudgetNanos * 0.6d));
        long recurringBudgetNanos = Math.max(100_000L, totalTaskBudgetNanos - oneTimeBudgetNanos);
        taskManager.updateBudget(oneTimeBudgetNanos, recurringBudgetNanos);
        physicsLoop.applySettings(settings.physics.snapshotQueueMode);
    }
    @Override
    public void close() {
        InertiaLogger.info("Closing world: " + worldName);

        if (buoyancyScanTask != null) {
            buoyancyScanTask.cancel();
        }

        physicsLoop.stop();
        objectManager.clearAll();

        if (terrainAdapter != null) terrainAdapter.onDisable();
        if (boundaryManager != null) boundaryManager.destroyBoundaries();

        BodyInterface bi = physicsSystem.getBodyInterface();
        for (Body b : staticBodies) {
            bi.removeBody(b.getId());
            bi.destroyBody(b.getId());
        }
        staticBodies.clear();
        systemStaticBodyIds.clear();

        physicsSystem.destroyAllBodies();

        InertiaPlugin plugin = InertiaPlugin.getInstance();
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, chunkTicketManager::releaseAll);
        } else {
            chunkTicketManager.releaseAll();
        }
    }

    public PhysicsSystem getPhysicsSystem() { return physicsSystem; }
    public BodyInterface getBodyInterface() { return physicsSystem.getBodyInterface(); }
    public WorldsConfig.WorldProfile getSettings() { return settings; }
    public void registerSystemStaticBody(int bodyId) { systemStaticBodyIds.add(bodyId); }
    public void unregisterSystemStaticBody(int bodyId) { systemStaticBodyIds.remove(bodyId); }
    public Set<Integer> getSystemStaticBodyIds() { return Collections.unmodifiableSet(systemStaticBodyIds); }

    private int countStaticBodies() {
        int count = systemStaticBodyIds.size();
        for (AbstractPhysicsBody obj : objectManager.getAll()) {
            if (obj.isValid() && obj.getMotionType() == MotionType.STATIC) count++;
        }
        return count;
    }

    public World getWorldBukkit() { return worldBukkit; }

    public @Nullable TerrainAdapter getTerrainAdapter() { return terrainAdapter; }

    public @Nullable ConstBody getBodyById(int id) {
        return new BodyLockRead(physicsSystem.getBodyLockInterfaceNoLock(), id).getBody();
    }

    @Override public @NotNull World getBukkitWorld() { return worldBukkit; }
    @Override
    public void setSimulationPaused(boolean paused) {
        boolean previous = this.isPaused.getAndSet(paused);
        if (previous == paused) {
            return;
        }
        eventDispatcher.dispatchSync(new PhysicsWorldPauseChangedEvent(
                new PhysicsWorldPauseChangedPayload(
                        PhysicsEventPayload.SCHEMA_VERSION_V1,
                        worldBukkit.getUID(),
                        worldName,
                        paused
                )
        ));
    }
    @Override public boolean isSimulationPaused() { return isPaused.get(); }
    @Override public void setGravity(@NotNull Vector gravity) { if (gravity == null) return; physicsSystem.setGravity(ConvertUtils.toVec3(gravity)); }
    @Override public @NotNull Vector getGravity() { Vec3 g = physicsSystem.getGravity(); return ConvertUtils.toBukkit(g); }
    @Override public @NotNull Collection<PhysicsBody> getBodies() { return objectManager.getAll().stream().map(obj -> (PhysicsBody) obj).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    @Override public @NotNull PhysicsInteraction getInteraction() {
        InertiaApiAccess.resolve().capabilities().require(ApiCapability.INTERACTION_ADVANCED);
        return queryEngine;
    }

    @Override
    public @NotNull ApiResult<PhysicsBody> createBodyResult(@NotNull PhysicsBodySpec spec) {
        Objects.requireNonNull(spec, "spec");
        Location spawnLocation = spec.location();
        if (spawnLocation.getWorld() == null) {
            InertiaLogger.warn("Cannot create API body: location.world is null");
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "error-occurred");
        }
        if (!spawnLocation.getWorld().getUID().equals(worldBukkit.getUID())) {
            InertiaLogger.warn("Cannot create API body: location world does not match PhysicsWorld");
            return ApiResult.failure(ApiErrorCode.WORLD_MISMATCH, "not-for-this-world");
        }
        if (!isInsideWorld(spawnLocation)) {
            InertiaLogger.debug("Attempted to spawn API body outside world boundaries at " + spawnLocation);
            return ApiResult.failure(ApiErrorCode.OUT_OF_BOUNDS, "spawn-fail-out-of-bounds");
        }

        ApiCapability shapeCapability = ApiCapabilities.forShape(spec.shape().kind());
        if (!InertiaApiAccess.resolve().capabilities().supports(shapeCapability)) {
            return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "shape-invalid-params");
        }

        try {
            ShapeRefC shape = apiShapeConverter.toJolt(spec.shape());
            RVec3 initialPos = toJolt(spawnLocation);
            Quaternionf rot = spec.rotation();
            Quat initialRot = new Quat(rot.x, rot.y, rot.z, rot.w);

            com.github.stephengold.joltjni.enumerate.EMotionType motionType = switch (spec.motionType()) {
                case STATIC -> com.github.stephengold.joltjni.enumerate.EMotionType.Static;
                case KINEMATIC -> com.github.stephengold.joltjni.enumerate.EMotionType.Kinematic;
                case DYNAMIC -> com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic;
            };

            int defaultLayer = (motionType == com.github.stephengold.joltjni.enumerate.EMotionType.Static)
                    ? PhysicsLayers.OBJ_STATIC
                    : PhysicsLayers.OBJ_MOVING;
            int layer = (spec.objectLayer() != null) ? spec.objectLayer() : defaultLayer;

            BodyCreationSettings settings = new BodyCreationSettings()
                    .setShape(shape)
                    .setMotionType(motionType)
                    .setObjectLayer(layer)
                    .setLinearDamping(spec.linearDamping())
                    .setAngularDamping(spec.angularDamping());

            if (motionType == com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic) {
                settings.setMotionQuality(com.github.stephengold.joltjni.enumerate.EMotionQuality.LinearCast);
                settings.getMassProperties().setMass(spec.mass());
            }

            settings.setFriction(spec.friction());
            settings.setRestitution(spec.restitution());
            settings.setGravityFactor(spec.gravityFactor());
            settings.setPosition(initialPos);
            settings.setRotation(initialRot);

            PhysicsBody spawnedBody = new CustomPhysicsBody(this, settings, spec.bodyId(), eventDispatcher);
            PhysicsBodyPreSpawnEvent preSpawnEvent = new PhysicsBodyPreSpawnEvent(spawnedBody);
            eventDispatcher.dispatchSync(preSpawnEvent);
            if (preSpawnEvent.isCancelled()) {
                spawnedBody.destroy();
                return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "spawn-cancelled");
            }
            eventDispatcher.dispatchSync(new PhysicsBodyPostSpawnEvent(spawnedBody));
            return ApiResult.success(spawnedBody);
        } catch (Exception e) {
            InertiaLogger.error("Failed to spawn API body in world " + worldName, e);
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "shape-invalid-params");
        }
    }


    public PhysicsEventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }
    public record RaycastResult(Long va, RVec3 hitPos) {}
}
