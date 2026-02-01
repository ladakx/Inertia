package com.ladakx.inertia.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.ladakx.inertia.common.chunk.ChunkTicketManager;
import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody;
import com.ladakx.inertia.physics.world.loop.PhysicsLoop;
import com.ladakx.inertia.physics.world.managers.PhysicsObjectManager;
import com.ladakx.inertia.physics.world.managers.PhysicsQueryEngine;
import com.ladakx.inertia.physics.world.managers.PhysicsTaskManager;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import com.ladakx.inertia.physics.world.snapshot.VisualUpdate;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.rendering.VisualEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PhysicsWorld implements AutoCloseable {

    // World Info
    private final String worldName;
    private final World worldBukkit;
    private final WorldsConfig.WorldProfile settings;

    // Jolt Core
    private final PhysicsSystem physicsSystem;
    private final JobSystem jobSystem;
    private final TempAllocator tempAllocator;

    // Sub-Systems
    private final PhysicsLoop physicsLoop;
    private final PhysicsObjectManager objectManager;
    private final PhysicsTaskManager taskManager;
    private final PhysicsQueryEngine queryEngine;
    private final ChunkTicketManager chunkTicketManager;
    private final TerrainAdapter terrainAdapter;

    // Static Bodies Tracking
    private final List<Body> staticBodies = new ArrayList<>(); // Terrain bodies

    public PhysicsWorld(World world,
                        WorldsConfig.WorldProfile settings,
                        JobSystem jobSystem,
                        TempAllocator tempAllocator,
                        TerrainAdapter terrainAdapter) {
        this.worldBukkit = world;
        this.worldName = world.getName();
        this.settings = settings;
        this.jobSystem = jobSystem;
        this.tempAllocator = tempAllocator;
        this.terrainAdapter = terrainAdapter;

        // Managers Init
        this.chunkTicketManager = new ChunkTicketManager(world);
        this.objectManager = new PhysicsObjectManager();
        this.taskManager = new PhysicsTaskManager();

        // Jolt Init
        this.physicsSystem = initializeJoltSystem();
        this.queryEngine = new PhysicsQueryEngine(physicsSystem, objectManager);

        // Terrain
        if (this.terrainAdapter != null) {
            this.terrainAdapter.onEnable(this);
        }

        // Loop Start
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
        sys.init(settings.maxBodies(), 0, 20_480, 65_536, layerMap, ovbFilter, ovoFilter);
        sys.setGravity(settings.gravity());
        sys.optimizeBroadPhase();
        return sys;
    }

    // --- Physics Thread Logic ---

    private void runPhysicsStep() {
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
        BodyInterface bodyInterface = physicsSystem.getBodyInterfaceNoLock();

        for (AbstractPhysicsBody obj : bodies) {
            if (obj instanceof DisplayedPhysicsBody displayed) {
                displayed.captureSnapshot(updates);
            }
            if (obj.getBody().isActive()) {
                RVec3 pos = bodyInterface.getCenterOfMassPosition(obj.getBody().getId());
                int chunkX = (int) Math.floor(pos.xx()) >> 4;
                int chunkZ = (int) Math.floor(pos.zz()) >> 4;
                activeChunks.add(ChunkUtils.getChunkKey(chunkX, chunkZ));
            }
        }
        return new PhysicsSnapshot(updates, activeChunks);
    }

    // --- Main Thread Logic ---

    private void applySnapshot(PhysicsSnapshot snapshot) {
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

    // --- Public API & Delegation ---

    public void createExplosion(@NotNull Vec3 origin, float force, float radius) {
        queryEngine.createExplosion(origin, force, radius);
    }

    public List<RaycastResult> raycastEntity(@NotNull Location start, @NotNull Vector dir, double dist) {
        return queryEngine.raycastEntity(start, dir, dist);
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
        return physicsSystem.getNumBodies() + amount <= settings.maxBodies();
    }

    // --- Constraints ---

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

    // --- Events ---

    public void onChunkLoad(int x, int z) {
        if (terrainAdapter != null) terrainAdapter.onChunkLoad(x, z);

        for (AbstractPhysicsBody obj : objectManager.getAll()) {
            if (obj instanceof DisplayedPhysicsBody displayed) {
                RVec3 pos = displayed.getBody().getPosition();
                if (((int) Math.floor(pos.xx()) >> 4) == x && ((int) Math.floor(pos.zz()) >> 4) == z) {
                    displayed.checkAndRestoreVisuals();
                }
            }
        }
    }

    public void onChunkUnload(int x, int z) {
        if (terrainAdapter != null) terrainAdapter.onChunkUnload(x, z);
    }

    // --- Cleanup ---

    @Override
    public void close() {
        InertiaLogger.info("Closing world: " + worldName);
        physicsLoop.stop();
        objectManager.clearAll();

        if (terrainAdapter != null) terrainAdapter.onDisable();

        BodyInterface bi = physicsSystem.getBodyInterface();
        for (Body b : staticBodies) {
            bi.removeBody(b.getId());
            bi.destroyBody(b.getId());
        }
        staticBodies.clear();

        physicsSystem.destroyAllBodies();
        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), chunkTicketManager::releaseAll);
    }

    // --- Getters ---
    public PhysicsSystem getPhysicsSystem() { return physicsSystem; }
    public BodyInterface getBodyInterface() { return physicsSystem.getBodyInterface(); }
    public WorldsConfig.WorldProfile getSettings() { return settings; }
    public World getWorldBukkit() { return worldBukkit; }
    public @Nullable ConstBody getBodyById(int id) {
        return new BodyLockRead(physicsSystem.getBodyLockInterfaceNoLock(), id).getBody();
    }

    // DTO for raycast results (re-declared to keep this class self-contained as requested by previous contexts)
    public record RaycastResult(Long va, RVec3 hitPos) {}
}