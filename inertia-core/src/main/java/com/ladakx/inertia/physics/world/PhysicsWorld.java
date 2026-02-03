package com.ladakx.inertia.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import com.github.stephengold.joltjni.readonly.ConstBody;
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
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final SnapshotPool snapshotPool;
    private final InertiaBodyActivationListener bodyActivationListener;

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
                this::runPhysicsStep,
                this::collectSnapshot,
                this::applySnapshot,
                () -> physicsSystem.getNumActiveBodies(EBodyType.RigidBody),
                physicsSystem::getNumBodies
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
        Collection<AbstractPhysicsBody> activeBodies = objectManager.getActive();
        List<VisualState> updates = snapshotPool.borrowList();
        java.util.Set<Long> activeChunks = new java.util.HashSet<>();
        List<AbstractPhysicsBody> toDestroy = new ArrayList<>();

        BodyInterface bodyInterface = physicsSystem.getBodyInterfaceNoLock();
        WorldsConfig.WorldSizeSettings sizeSettings = settings.size();

        for (AbstractPhysicsBody obj : activeBodies) {
            if (!obj.isValid()) continue;

            if (obj.getBody().isActive()) {
                RVec3 joltPos = bodyInterface.getCenterOfMassPosition(obj.getBody().getId());

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
                activeChunks.add(ChunkUtils.getChunkKey(chunkX, chunkZ));
            }

            if (obj instanceof DisplayedPhysicsBody displayed) {
                displayed.captureSnapshot(updates, snapshotPool, origin);
            }
        }

        return new PhysicsSnapshot(updates, activeChunks, toDestroy);
    }

    private void applySnapshot(PhysicsSnapshot snapshot) {
        for (AbstractPhysicsBody body : snapshot.bodiesToDestroy()) {
            if (body.isValid()) {
                schedulePhysicsTask(body::destroy);
            }
        }

        for (VisualState state : snapshot.updates()) {
            VisualEntity visual = state.getVisual();
            if (visual.isValid()) {
                Location loc = new Location(worldBukkit, state.getPosition().x, state.getPosition().y, state.getPosition().z);
                visual.update(loc, state.getRotation(), state.getCenterOffset(), state.isRotateTranslation());
                visual.setVisible(state.isVisible());
            }
        }

        chunkTicketManager.updateTickets(snapshot.activeChunkKeys());
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

    public void createExplosion(@NotNull Vec3 originLocal, float force, float radius) {
        Location loc = new Location(worldBukkit, originLocal.getX() + origin.xx(), originLocal.getY() + origin.yy(), originLocal.getZ() + origin.zz());
        queryEngine.createExplosion(loc, force, radius);
    }

    public List<RaycastResult> raycastEntity(@NotNull Location start, @NotNull Vector dir, double dist) {
        RaycastHit hit = queryEngine.raycast(start, dir, dist);
        if (hit == null) {
            return Collections.emptyList();
        }

        if (hit.body() instanceof AbstractPhysicsBody abstractBody) {
            long va = abstractBody.getBody().targetVa();
            Location hitLoc = hit.point().toLocation(worldBukkit);
            RVec3 hitPosLocal = toJolt(hitLoc);
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