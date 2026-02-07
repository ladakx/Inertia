package com.ladakx.inertia.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.api.interaction.PhysicsInteraction;
import com.ladakx.inertia.api.interaction.RaycastHit;
import com.ladakx.inertia.api.service.PhysicsMetricsService;
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
import com.ladakx.inertia.physics.world.managers.*;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import com.ladakx.inertia.physics.world.snapshot.SnapshotPool;
import com.ladakx.inertia.physics.world.snapshot.VisualState;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.rendering.NetworkEntityTracker;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PhysicsWorld implements AutoCloseable, IPhysicsWorld {

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
    private final WorldBoundaryManager boundaryManager;
    private final List<Body> staticBodies = new ArrayList<>();
    private final Set<Integer> systemStaticBodyIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final SnapshotPool snapshotPool;
    private final InertiaBodyActivationListener bodyActivationListener;

    // --- CHANGED: Use Singleton NetworkEntityTracker ---
    private final NetworkEntityTracker networkEntityTracker;

    public PhysicsWorld(World world,
                        WorldsConfig.WorldProfile settings,
                        PhysicsSystem physicsSystem,
                        JobSystem jobSystem,
                        TempAllocator tempAllocator,
                        TerrainAdapter terrainAdapter,
                        PhysicsMetricsService metricsService) {
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
        this.snapshotPool = new SnapshotPool();
        this.queryEngine = new PhysicsQueryEngine(this, physicsSystem, objectManager);

        this.networkEntityTracker = InertiaPlugin.getInstance().getNetworkEntityTracker();

        this.contactListener = new PhysicsContactListener(objectManager);
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
                worldName,
                settings.tickRate(),
                settings.performance().maxBodies(),
                this::runPhysicsStep,   // ASYNC
                this::collectSnapshot,  // ASYNC
                this::applySnapshot,    // SYNC (Main Thread)
                () -> physicsSystem.getNumActiveBodies(EBodyType.RigidBody),
                physicsSystem::getNumBodies,
                this::countStaticBodies
        );

        if (metricsService != null) {
            this.physicsLoop.addListener(metricsService);
        }
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
        List<AbstractPhysicsBody> allBodies = objectManager.getAll();
        List<VisualState> updates = snapshotPool.borrowList();
        java.util.Set<Long> bodiesChunkKeys = new java.util.HashSet<>();
        List<AbstractPhysicsBody> toDestroy = new ArrayList<>();

        BodyInterface bodyInterface = physicsSystem.getBodyInterfaceNoLock();
        WorldsConfig.WorldSizeSettings sizeSettings = settings.size();

        for (AbstractPhysicsBody obj : allBodies) {
            if (!obj.isValid()) continue;

            // Collect logic... (Bounds check, etc)
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

            // Chunk keys logic
            double worldX = joltPos.xx() + origin.xx();
            double worldZ = joltPos.zz() + origin.zz();
            int chunkX = (int) Math.floor(worldX) >> 4;
            int chunkZ = (int) Math.floor(worldZ) >> 4;
            bodiesChunkKeys.add(ChunkUtils.getChunkKey(chunkX, chunkZ));

            if (obj instanceof DisplayedPhysicsBody displayed) {
                // Capture visual state (Thread-safe read from Jolt)
                displayed.captureSnapshot(updates, snapshotPool, origin);
            }
        }
        return new PhysicsSnapshot(updates, bodiesChunkKeys, toDestroy);
    }

    /**
     * Executes on the Main Server Thread
     */
    private void applySnapshot(PhysicsSnapshot snapshot) {
        // 1. Process Logic updates (Destroy bodies, load chunks)
        for (AbstractPhysicsBody body : snapshot.bodiesToDestroy()) {
            if (body.isValid()) {
                schedulePhysicsTask(body::destroy);
            }
        }
        chunkTicketManager.updateTickets(snapshot.activeChunkKeys());

        // 2. Update Network Tracker State (Update internal positions of visuals)
        if (snapshot.updates() != null) {
            Location mutableLoc = new Location(worldBukkit, 0, 0, 0); // Reuse to avoid allocations

            for (VisualState state : snapshot.updates()) {
                if (state.getVisual() != null) {
                    mutableLoc.setX(state.getPosition().x);
                    mutableLoc.setY(state.getPosition().y);
                    mutableLoc.setZ(state.getPosition().z);
                    // Yaw/Pitch are usually derived from rotation quaternion if needed,
                    // but for Display entities we pass the quaternion directly.

                    networkEntityTracker.updateState(
                            state.getVisual(),
                            mutableLoc,
                            state.getRotation()
                    );
                }
            }
        }

        // 3. Tick Tracker (Calculate visibility and Send Packets)
        // Calculate view distance
        int viewDistanceBlocks = worldBukkit.getViewDistance() * 16;
        double viewDistanceSquared = (double) viewDistanceBlocks * viewDistanceBlocks;

        networkEntityTracker.tick(worldBukkit.getPlayers(), viewDistanceSquared);

        // 4. Cleanup
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

    // ... Copy remaining methods from Context to ensure no code loss ...
    public void createExplosion(@NotNull com.github.stephengold.joltjni.Vec3 originLocal, float force, float radius) {
        Location loc = new Location(worldBukkit, originLocal.getX() + origin.xx(), originLocal.getY() + origin.yy(), originLocal.getZ() + origin.zz());
        queryEngine.createExplosion(loc, force, radius);
    }

    public List<RaycastResult> raycastEntity(@NotNull Location start, @NotNull Vector dir, double dist) {
        RaycastHit hit = queryEngine.raycast(start, dir, dist);
        if (hit == null) return Collections.emptyList();
        if (hit.body() instanceof AbstractPhysicsBody abstractBody) {
            long va = abstractBody.getBody().targetVa();
            Location hitLoc = hit.point().toLocation(worldBukkit);
            RVec3 hitPosLocal = toJolt(hitLoc);
            return Collections.singletonList(new RaycastResult(va, hitPosLocal));
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
    public @NotNull List<AbstractPhysicsBody> getObjects() { return objectManager.getAll(); }
    public UUID addTickTask(Runnable runnable) { return taskManager.addTickTask(runnable); }
    public void removeTickTask(UUID uuid) { taskManager.removeTickTask(uuid); }
    public void schedulePhysicsTask(Runnable task) { taskManager.schedule(task); }
    public boolean canSpawnBodies(int amount) { return physicsSystem.getNumBodies() + amount <= settings.performance().maxBodies(); }

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
        systemStaticBodyIds.clear();
        physicsSystem.destroyAllBodies();

        InertiaPlugin plugin = InertiaPlugin.getInstance();
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, chunkTicketManager::releaseAll);
        } else {
            chunkTicketManager.releaseAll();
        }
    }

    // Getters
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
    public @Nullable ConstBody getBodyById(int id) {
        return new BodyLockRead(physicsSystem.getBodyLockInterfaceNoLock(), id).getBody();
    }
    @Override public @NotNull World getBukkitWorld() { return worldBukkit; }
    @Override public void setSimulationPaused(boolean paused) { this.isPaused.set(paused); }
    @Override public boolean isSimulationPaused() { return isPaused.get(); }
    @Override public void setGravity(@NotNull Vector gravity) { if (gravity == null) return; physicsSystem.setGravity(ConvertUtils.toVec3(gravity)); }
    @Override public @NotNull Vector getGravity() { Vec3 g = physicsSystem.getGravity(); return ConvertUtils.toBukkit(g); }
    @Override public @NotNull Collection<InertiaPhysicsBody> getBodies() { return Collections.unmodifiableCollection(objectManager.getAll()); }
    @Override public @NotNull PhysicsInteraction getInteraction() { return queryEngine; }
    public record RaycastResult(Long va, RVec3 hitPos) {}
}