package com.ladakx.inertia.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.ladakx.inertia.api.interaction.PhysicsInteraction;
import com.ladakx.inertia.api.interaction.RaycastHit;
import com.ladakx.inertia.api.world.IPhysicsWorld;
import com.ladakx.inertia.common.chunk.ChunkTicketManager;
import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody;
import com.ladakx.inertia.physics.world.loop.PhysicsLoop;
import com.ladakx.inertia.physics.world.managers.PhysicsContactListener;
import com.ladakx.inertia.physics.world.managers.PhysicsObjectManager;
import com.ladakx.inertia.physics.world.managers.PhysicsQueryEngine;
import com.ladakx.inertia.physics.world.managers.PhysicsTaskManager;
import com.ladakx.inertia.physics.world.managers.WorldBoundaryManager;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import com.ladakx.inertia.physics.world.snapshot.VisualUpdate;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.rendering.VisualEntity;
import com.ladakx.inertia.common.utils.ConvertUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PhysicsWorld implements AutoCloseable, IPhysicsWorld {

    private final String worldName;
    private final World worldBukkit;
    private final WorldsConfig.WorldProfile settings;
    private final RVec3 origin; // World Center in Minecraft Coordinates

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
    private final WorldBoundaryManager boundaryManager;

    private final List<Body> staticBodies = new ArrayList<>();
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    public PhysicsWorld(World world,
                        WorldsConfig.WorldProfile settings,
                        JobSystem jobSystem,
                        TempAllocator tempAllocator,
                        TerrainAdapter terrainAdapter) {
        this.worldBukkit = world;
        this.worldName = world.getName();
        this.settings = settings;
        this.origin = settings.size().origin();
        this.jobSystem = jobSystem;
        this.tempAllocator = tempAllocator;
        this.terrainAdapter = terrainAdapter;

        this.chunkTicketManager = new ChunkTicketManager(world);
        this.objectManager = new PhysicsObjectManager();
        this.taskManager = new PhysicsTaskManager();

        this.physicsSystem = initializeJoltSystem();
        this.queryEngine = new PhysicsQueryEngine(this, physicsSystem, objectManager);
        this.contactListener = new PhysicsContactListener(objectManager);
        this.physicsSystem.setContactListener(contactListener);

        // Initialize Boundary Manager
        this.boundaryManager = new WorldBoundaryManager(this, settings.size());
        this.boundaryManager.createBoundaries();

        if (this.terrainAdapter != null) {
            this.terrainAdapter.onEnable(this);
        }

        this.physicsLoop = new PhysicsLoop(
                worldName,
                settings.tickRate(),
                this::runPhysicsStep,
                this::collectSnapshot,
                this::applySnapshot
        );
    }

    private PhysicsSystem initializeJoltSystem() {
        ObjectLayerPairFilterTable ovoFilter = new ObjectLayerPairFilterTable(PhysicsLayers.NUM_OBJ_LAYERS);
        ovoFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_MOVING);
        ovoFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_STATIC);
        ovoFilter.disableCollision(PhysicsLayers.OBJ_STATIC, PhysicsLayers.OBJ_STATIC);

        BroadPhaseLayerInterfaceTable layerMap = new BroadPhaseLayerInterfaceTable(PhysicsLayers.NUM_OBJ_LAYERS, PhysicsLayers.NUM_BP_LAYERS);
        layerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_MOVING, 0);
        layerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_STATIC, 0);

        ObjectVsBroadPhaseLayerFilter ovbFilter = new ObjectVsBroadPhaseLayerFilterTable(layerMap, PhysicsLayers.NUM_BP_LAYERS, ovoFilter, PhysicsLayers.NUM_OBJ_LAYERS);

        PhysicsSystem sys = new PhysicsSystem();
        sys.init(
                settings.performance().maxBodies(),
                settings.performance().numBodyMutexes(),
                settings.performance().maxBodyPairs(),
                settings.performance().maxContactConstraints(),
                layerMap,
                ovbFilter,
                ovoFilter
        );

        PhysicsSettings physSettings = new PhysicsSettings();
        WorldsConfig.SolverSettings solverSettings = settings.solver();
        physSettings.setNumVelocitySteps(solverSettings.velocityIterations());
        physSettings.setNumPositionSteps(solverSettings.positionIterations());
        physSettings.setBaumgarte(solverSettings.baumgarte());
        physSettings.setSpeculativeContactDistance(solverSettings.speculativeContactDistance());
        physSettings.setPenetrationSlop(solverSettings.penetrationSlop());
        physSettings.setLinearCastThreshold(solverSettings.linearCastThreshold());
        physSettings.setLinearCastMaxPenetration(solverSettings.linearCastMaxPenetration());
        physSettings.setManifoldTolerance(solverSettings.manifoldTolerance());
        physSettings.setMaxPenetrationDistance(solverSettings.maxPenetrationDistance());
        physSettings.setConstraintWarmStart(solverSettings.constraintWarmStart());
        physSettings.setUseBodyPairContactCache(solverSettings.useBodyPairContactCache());
        physSettings.setUseLargeIslandSplitter(solverSettings.useLargeIslandSplitter());
        physSettings.setAllowSleeping(solverSettings.allowSleeping());
        physSettings.setDeterministicSimulation(solverSettings.deterministicSimulation());

        WorldsConfig.SleepSettings sleepSettings = settings.sleeping();
        physSettings.setTimeBeforeSleep(sleepSettings.timeBeforeSleep());
        physSettings.setPointVelocitySleepThreshold(sleepSettings.pointVelocityThreshold());

        sys.setPhysicsSettings(physSettings);
        sys.setGravity(settings.gravity());
        sys.optimizeBroadPhase();

        return sys;
    }

    private void runPhysicsStep() {
        if (isPaused.get()) {
            return;
        }

        float deltaTime = 1.0f / settings.tickRate();
        int errors = physicsSystem.update(deltaTime, settings.collisionSteps(), tempAllocator, jobSystem);

        if (errors != EPhysicsUpdateError.None) {
            InertiaLogger.warn("Physics error in world " + worldName + ": " + errors);
        }

        taskManager.runAll();
    }

    private PhysicsSnapshot collectSnapshot() {
        List<AbstractPhysicsBody> bodies = objectManager.getAll();
        List<VisualUpdate> updates = new ArrayList<>(bodies.size());
        java.util.Set<Long> activeChunks = new java.util.HashSet<>();
        List<AbstractPhysicsBody> toDestroy = new ArrayList<>();
        BodyInterface bodyInterface = physicsSystem.getBodyInterfaceNoLock();
        WorldsConfig.WorldSizeSettings sizeSettings = settings.size();

        for (AbstractPhysicsBody obj : bodies) {
            if (!obj.isValid()) continue;

            // Boundary Checks
            if (obj.getBody().isActive()) {
                RVec3 joltPos = bodyInterface.getCenterOfMassPosition(obj.getBody().getId());

                // 1. Kill below Min Y
                if (sizeSettings.killBelowMinY() && boundaryManager.isBelowBottom(joltPos)) {
                    toDestroy.add(obj);
                    continue; // Skip further processing for this object
                }

                // 2. Prevent Exit
                if (sizeSettings.preventExit() && !boundaryManager.isInside(joltPos)) {
                    toDestroy.add(obj);
                    continue; // Skip
                }

                // Chunk Ticket Updates
                // Convert Jolt Position to Bukkit Position manually for efficiency
                double worldX = joltPos.xx() + origin.xx();
                double worldZ = joltPos.zz() + origin.zz();

                int chunkX = (int) Math.floor(worldX) >> 4;
                int chunkZ = (int) Math.floor(worldZ) >> 4;
                activeChunks.add(ChunkUtils.getChunkKey(chunkX, chunkZ));
            }

            // Visual Updates
            if (obj instanceof DisplayedPhysicsBody displayed) {
                displayed.captureSnapshot(updates);
            }
        }
        return new PhysicsSnapshot(updates, activeChunks, toDestroy);
    }

    private void applySnapshot(PhysicsSnapshot snapshot) {
        // Handle Destruction
        for (AbstractPhysicsBody body : snapshot.bodiesToDestroy()) {
            if (body.isValid()) {
                schedulePhysicsTask(() -> body.destroy());
            }
        }

        // Handle Visuals
        for (VisualUpdate update : snapshot.updates()) {
            VisualEntity visual = update.visual();
            if (visual.isValid()) {
                Location loc = new Location(worldBukkit, update.position().x, update.position().y, update.position().z);
                visual.update(loc, update.rotation(), update.centerOffset(), update.rotateTranslation());
                visual.setVisible(update.visible());
            }
        }
        chunkTicketManager.updateTickets(snapshot.activeChunkKeys());
    }

    /**
     * Checks if the given shape overlaps with any STATIC objects at the specified position/rotation.
     * Used to prevent spawning objects inside walls or floors.
     *
     * @param shape    The shape to test
     * @param position The world position (RVec3)
     * @param rotation The rotation (Quat)
     * @return true if an overlap is detected, false otherwise.
     */
    public boolean checkOverlap(@NotNull com.github.stephengold.joltjni.readonly.ConstShape shape,
                                @NotNull RVec3 position,
                                @NotNull Quat rotation) {
        CollideShapeSettings settings = new CollideShapeSettings();
        settings.setActiveEdgeMode(EActiveEdgeMode.CollideOnlyWithActive);

        // Используем AnyHitCollector, так как нам достаточно знать сам факт пересечения (быстро)
        // Фильтруем: Ищем пересечения только со статическими объектами (PhysicsLayers.OBJ_STATIC)
        // В BroadPhase статика обычно находится в слое 0.
        try (AnyHitCollideShapeCollector collector = new AnyHitCollideShapeCollector();
             SpecifiedBroadPhaseLayerFilter bpFilter = new SpecifiedBroadPhaseLayerFilter(0);
             SpecifiedObjectLayerFilter objFilter = new SpecifiedObjectLayerFilter(PhysicsLayers.OBJ_STATIC)) {

            physicsSystem.getNarrowPhaseQuery().collideShape(
                    shape,
                    Vec3.sReplicate(1.0f), // Scale 1.0
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

    // --- Coordinate Conversion Methods ---

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

    public RVec3 getOrigin() {
        return origin;
    }

    public boolean isInsideWorld(Location location) {
        if (!location.getWorld().getName().equals(worldName)) return false;
        return boundaryManager.isInside(toJolt(location));
    }

    public WorldBoundaryManager getBoundaryManager() {
        return boundaryManager;
    }

    // -------------------------------------

    public void createExplosion(@NotNull Vec3 originLocal, float force, float radius) {
        Location loc = new Location(worldBukkit, originLocal.getX() + origin.xx(), originLocal.getY() + origin.yy(), originLocal.getZ() + origin.zz());
        queryEngine.createExplosion(loc, force, radius);
    }

    public List<RaycastResult> raycastEntity(@NotNull Location start, @NotNull Vector dir, double dist) {
        RaycastHit hit = queryEngine.raycast(start, dir, dist);
        if (hit == null) return Collections.emptyList();

        RVec3 hitPosLocal = toJolt(hit.point().toLocation(worldBukkit));

        if (hit.body() instanceof AbstractPhysicsBody abstractBody) {
            long va = abstractBody.getBody().targetVa();
            return Collections.singletonList(new RaycastResult(va, hitPosLocal));
        }
        return Collections.emptyList();
    }

    public void addObject(AbstractPhysicsBody object) {
        objectManager.add(object);
    }

    public void removeObject(AbstractPhysicsBody object) {
        objectManager.remove(object);
    }

    public void registerBody(AbstractPhysicsBody object, Body body) {
        objectManager.registerBody(object, body);
    }

    public @Nullable AbstractPhysicsBody getObjectByVa(long va) {
        return objectManager.getByVa(va);
    }

    public @Nullable AbstractPhysicsBody getObjectByUuid(UUID uuid) {
        return objectManager.getByUuid(uuid);
    }

    public @NotNull List<AbstractPhysicsBody> getObjects() {
        return objectManager.getAll();
    }

    public UUID addTickTask(Runnable runnable) {
        return taskManager.addTickTask(runnable);
    }

    public void removeTickTask(UUID uuid) {
        taskManager.removeTickTask(uuid);
    }

    public void schedulePhysicsTask(Runnable task) {
        taskManager.schedule(task);
    }

    public boolean canSpawnBodies(int amount) {
        return physicsSystem.getNumBodies() + amount <= settings.performance().maxBodies();
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

    @Override
    public void close() {
        InertiaLogger.info("Closing world: " + worldName);
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

        physicsSystem.destroyAllBodies();
        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), chunkTicketManager::releaseAll);
    }

    public PhysicsSystem getPhysicsSystem() {
        return physicsSystem;
    }

    public BodyInterface getBodyInterface() {
        return physicsSystem.getBodyInterface();
    }

    public WorldsConfig.WorldProfile getSettings() {
        return settings;
    }

    public World getWorldBukkit() {
        return worldBukkit;
    }

    public @Nullable ConstBody getBodyById(int id) {
        return new BodyLockRead(physicsSystem.getBodyLockInterfaceNoLock(), id).getBody();
    }

    @Override
    public @NotNull World getBukkitWorld() {
        return worldBukkit;
    }

    @Override
    public void setSimulationPaused(boolean paused) {
        this.isPaused.set(paused);
    }

    @Override
    public boolean isSimulationPaused() {
        return isPaused.get();
    }

    @Override
    public void setGravity(@NotNull Vector gravity) {
        if (gravity == null) return;
        physicsSystem.setGravity(ConvertUtils.toVec3(gravity));
    }

    @Override
    public @NotNull Vector getGravity() {
        Vec3 g = physicsSystem.getGravity();
        return ConvertUtils.toBukkit(g);
    }

    @Override
    public @NotNull Collection<InertiaPhysicsBody> getBodies() {
        return Collections.unmodifiableCollection(objectManager.getAll());
    }

    @Override
    public @NotNull PhysicsInteraction getInteraction() {
        return queryEngine;
    }

    public record RaycastResult(Long va, RVec3 hitPos) {
    }
}