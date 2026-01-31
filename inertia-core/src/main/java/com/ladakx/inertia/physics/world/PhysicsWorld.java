package com.ladakx.inertia.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBroadPhaseQuery;
import com.github.stephengold.joltjni.readonly.ConstPlane;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.common.chunk.ChunkTicketManager;
import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import com.ladakx.inertia.physics.world.snapshot.VisualUpdate;
import com.ladakx.inertia.rendering.VisualEntity;
import com.ladakx.inertia.common.utils.MiscUtils;
import com.ladakx.inertia.common.utils.ConvertUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.RVec3;

public class PhysicsWorld implements AutoCloseable {

    private final String worldName;
    private final World worldBukkit;
    private final WorldsConfig.WorldProfile settings;
    private final PhysicsSystem physicsSystem;

    // Global resources
    private final JobSystem jobSystem;
    private final TempAllocator tempAllocator;

    // Physics Loop
    private final ScheduledExecutorService tickExecutor;
    private final AtomicBoolean isActive = new AtomicBoolean(true);

    // Snapshot Sync
    private final AtomicReference<PhysicsSnapshot> snapshotBuffer = new AtomicReference<>();
    private final BukkitTask syncTask;

    // Objects
    private final @NotNull List<AbstractPhysicsBody> objects = new CopyOnWriteArrayList<>();

    private final @NotNull Map<Long, AbstractPhysicsBody> objectMap = new ConcurrentHashMap<>(); // va -> object
    private final @NotNull Map<UUID, AbstractPhysicsBody> uuidMap = new ConcurrentHashMap<>(); // uuid -> object

    private final List<Body> staticBodies = new CopyOnWriteArrayList<>();

    // Custom Tasks
    private final Map<UUID, Runnable> tickTasks = new ConcurrentHashMap<>();
    private final Queue<Runnable> physicsTasks = new ConcurrentLinkedQueue<>();

    // Chunk Ticket Manager
    private final ChunkTicketManager chunkTicketManager; // New field

    public PhysicsWorld(World world, WorldsConfig.WorldProfile settings, JobSystem jobSystem, TempAllocator tempAllocator) {
        // Basic Setup
        this.worldBukkit = world;
        this.worldName = world.getName();
        this.settings = settings;
        this.jobSystem = jobSystem;
        this.tempAllocator = tempAllocator;

        // Initialize Ticket Manager
        this.chunkTicketManager = new ChunkTicketManager(world);

        // --- Layers Setup ---
        ObjectLayerPairFilterTable ovoFilter = new ObjectLayerPairFilterTable(PhysicsLayers.NUM_OBJ_LAYERS);
        ovoFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_MOVING);
        ovoFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_STATIC);
        ovoFilter.disableCollision(PhysicsLayers.OBJ_STATIC, PhysicsLayers.OBJ_STATIC);

        BroadPhaseLayerInterfaceTable layerMap = new BroadPhaseLayerInterfaceTable(PhysicsLayers.NUM_OBJ_LAYERS, PhysicsLayers.NUM_BP_LAYERS);
        layerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_MOVING, 0);
        layerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_STATIC, 0);

        ObjectVsBroadPhaseLayerFilter ovbFilter = new ObjectVsBroadPhaseLayerFilterTable(layerMap, PhysicsLayers.NUM_BP_LAYERS, ovoFilter, PhysicsLayers.NUM_OBJ_LAYERS);

        // --- Jolt Initialization ---
        this.physicsSystem = new PhysicsSystem();
        this.physicsSystem.init(
                settings.maxBodies(),
                0,
                20_480,
                65_536,
                layerMap,
                ovbFilter,
                ovoFilter
        );

        this.physicsSystem.setGravity(this.settings.gravity());
        this.physicsSystem.optimizeBroadPhase();

        // --- Thread Setup ---
        // 1. Physics Thread (Producer)
        this.tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "inertia-space-" + worldName);
            t.setDaemon(true);
            return t;
        });

        // 2. Sync Task (Consumer)
        this.syncTask = Bukkit.getScheduler().runTaskTimer(InertiaPlugin.getInstance(), this::consumeSnapshot, 1L, 1L);

        if (this.settings.floorPlane().enabled()) {
            addFloorPlane(this.settings.floorPlane());
        }

        startSimulationLoop(this.settings.tickRate());
    }

    private void startSimulationLoop(int fps) {
        if (fps <= 0) fps = 20;

        long periodMicros = 1_000_000 / fps;
        float deltaTime = 1.0f / fps;

        tickExecutor.scheduleAtFixedRate(() -> {
            if (!isActive.get()) return;

            try {
                // --- Step A: Simulation ---
                // Run physics tick
                int errors = physicsSystem.update(deltaTime, this.settings.collisionSteps(), tempAllocator, jobSystem);
                if (errors != EPhysicsUpdateError.None) {
                    InertiaLogger.warn("Physics error in world " + worldName + ": " + errors);
                }

                // Run queued physics tasks
                Runnable task;
                while ((task = physicsTasks.poll()) != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        InertiaLogger.error("Error while executing physics task in world " + worldName, e);
                    }
                }

                // Run internal logic tasks (e.g. Grabber Tool forces)
                for (Runnable tickTask : tickTasks.values()) {
                    try {
                        tickTask.run();
                    } catch (Exception e) {
                        InertiaLogger.error("Error in physics tick task", e);
                    }
                }

                // --- Step B: Snapshot Preparation (Producer) ---
                if (snapshotBuffer.get() == null) {
                    List<VisualUpdate> updates = new ArrayList<>(objects.size());
                    java.util.Set<Long> activeChunks = new java.util.HashSet<>(); // Collect active chunks

                    // BodyInterface for reading positions efficiently
                    BodyInterface bodyInterface = physicsSystem.getBodyInterfaceNoLock();

                    for (AbstractPhysicsBody obj : objects) {
                        // 1. Capture Visuals
                        if (obj instanceof DisplayedPhysicsBody displayed) {
                            displayed.captureSnapshot(updates);
                        }

                        // 2. Capture Active Chunks
                        // Only check active bodies to force-load their chunks. Sleeping bodies don't need forced chunks.
                        if (obj.getBody().isActive()) {
                            RVec3 pos = bodyInterface.getCenterOfMassPosition(obj.getBody().getId());
                            int chunkX = (int) Math.floor(pos.xx()) >> 4;
                            int chunkZ = (int) Math.floor(pos.zz()) >> 4;
                            activeChunks.add(ChunkUtils.getChunkKey(chunkX, chunkZ));
                        }
                    }

                    // Atomic publish
                    // Even if updates is empty, we might need to send activeChunks to keep chunks loaded or unload them
                    snapshotBuffer.set(new PhysicsSnapshot(updates, activeChunks));
                }
            } catch (Exception e) {
                InertiaLogger.error("Error in physics tick for world " + worldName, e);
            }
        }, 0, periodMicros, TimeUnit.MICROSECONDS);
    }

    /**
     * Executed on Bukkit Main Thread every tick.
     * Consumes the latest snapshot and applies it to entities.
     */
    /**
     * Executed on Bukkit Main Thread every tick.
     * Consumes the latest snapshot and applies it to entities.
     */
    private void consumeSnapshot() {
        if (!isActive.get()) return;

        PhysicsSnapshot snapshot = snapshotBuffer.getAndSet(null);

        if (snapshot == null) return;

        // 1. Apply Visual Updates
        for (VisualUpdate update : snapshot.updates()) {
            VisualEntity visual = update.visual();
            if (visual.isValid()) {
                Location loc = new Location(
                        worldBukkit,
                        update.position().x,
                        update.position().y,
                        update.position().z
                );

                visual.update(loc, update.rotation(), update.centerOffset(), update.rotateTranslation());
                visual.setVisible(update.visible());
            }
        }

        // 2. Apply Chunk Loading Tickets (Sync with Main Thread)
        // This prevents active bodies from falling into void or glitching when chunks unload
        chunkTicketManager.updateTickets(snapshot.activeChunkKeys());
    }

    @Override
    public void close() {
        if (isActive.compareAndSet(true, false)) {
            InertiaLogger.info("Closing physics space for world: " + worldName);

            // Stop consumer
            if (syncTask != null && !syncTask.isCancelled()) {
                syncTask.cancel();
            }

            removeAllObjects();

            BodyInterface bi = physicsSystem.getBodyInterface();
            for (Body b : staticBodies) {
                bi.removeBody(b.getId());
                bi.destroyBody(b.getId());
            }
            staticBodies.clear();

            // Stop producer
            tickExecutor.shutdown();
            try {
                if (!tickExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    tickExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                tickExecutor.shutdownNow();
            }

            physicsSystem.destroyAllBodies();

            // Release all forced chunks
            Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), chunkTicketManager::releaseAll);
        }
    }

    // --- Standard Methods (Getters, Add/Remove) ---

    public PhysicsSystem getPhysicsSystem() { return physicsSystem; }
    public BodyInterface getBodyInterface() { return physicsSystem.getBodyInterface(); }
    public WorldsConfig.WorldProfile getSettings() { return settings; }
    public World getWorldBukkit() { return worldBukkit; }
    public @NotNull List<AbstractPhysicsBody> getObjects() { return objects; }

    public @Nullable ConstBody getBodyById(int id) {
        return new BodyLockRead(physicsSystem.getBodyLockInterfaceNoLock(), id).getBody();
    }

    public UUID addTickTask(Runnable runnable) {
        UUID random = UUID.randomUUID();
        tickTasks.put(random, runnable);
        return random;
    }

    public void removeTickTask(UUID uuid) {
        tickTasks.remove(uuid);
    }

    public void schedulePhysicsTask(@NotNull Runnable task) {
        if (task == null) {
            return;
        }
        physicsTasks.add(task);
    }

    /**
     * Remove and destroy all physics objects currently registered in this space.
     * Uses the object's {@link AbstractPhysicsBody#destroy()} implementation,
     * which is safe and idempotent.
     */
    public void removeAllObjects() {
        List<AbstractPhysicsBody> snapshot = new ArrayList<>(objects);
        int count = 0;
        for (AbstractPhysicsBody obj : snapshot) {
            try {
                obj.destroy();
                count++;
            } catch (Exception e) {
                InertiaLogger.error(
                        "Failed to destroy physics object while clearing world " + worldName,
                        e
                );
            }
        }
        InertiaLogger.info("Cleared " + count + " physics objects from world " + worldName);
    }

    /**
     * Register a new physics object along with its primary body.
     *
     * @param object physics object to add (not null)
     */
    public void addObject(AbstractPhysicsBody object) {
        objects.add(object);
        registerBody(object, object.getBody());
        uuidMap.put(object.getUuid(), object);
    }

    /**
     * Register an additional body that belongs to the specified physics object.
     * This is used for multi-body constructs such as chains and ragdolls so that
     * tools like DeleteTool can correctly resolve ownership from any hit body.
     *
     * @param object owning physics object (not null)
     * @param body   Jolt body belonging to the object (not null)
     */
    public void registerBody(@NotNull AbstractPhysicsBody object, @NotNull Body body) {
        if (body == null) {
            return;
        }
        objectMap.put(body.va(), object);
    }

    public @Nullable AbstractPhysicsBody getObjectByVa(long va) {
        return objectMap.get(va);
    }
    public @Nullable AbstractPhysicsBody getObjectByUuid(UUID uuid) { return uuidMap.get(uuid);}

    /**
     * Unregister a physics object from this space.
     * All body mappings that point to the object are removed.
     *
     * @param object physics object to remove (not null)
     */
    public void removeObject(AbstractPhysicsBody object) {
        objects.remove(object);
        objectMap.entrySet().removeIf(entry -> entry.getValue() == object);
        uuidMap.remove(object.getUuid());
    }

    public void addConstraint(Constraint constraint) {
        physicsSystem.addConstraint(constraint);
        if (constraint instanceof TwoBodyConstraint twoBodyConstraint) {
            AbstractPhysicsBody obj1 = getObjectByVa(twoBodyConstraint.getBody1().va());
            TwoBodyConstraintRef ref = twoBodyConstraint.toRef();
            if (obj1 != null) obj1.addRelatedConstraint(ref);
            AbstractPhysicsBody obj2 = getObjectByVa(twoBodyConstraint.getBody2().va());
            if (obj2 != null) obj2.addRelatedConstraint(ref);
        }
    }

    /**
     * Remove a constraint from the physics system using a Jolt {@link Constraint} instance.
     * Also updates all owning {@link AbstractPhysicsBody} instances so they no longer
     * track the constraint.
     *
     * @param constraint constraint to remove (not null)
     */
    public void removeConstraint(Constraint constraint) {
        physicsSystem.removeConstraint(constraint);
        if (constraint instanceof TwoBodyConstraint twoBodyConstraint) {
            TwoBodyConstraintRef ref = twoBodyConstraint.toRef();
            AbstractPhysicsBody obj1 = getObjectByVa(twoBodyConstraint.getBody1().va());
            if (obj1 != null) obj1.removeRelatedConstraint(ref);
            AbstractPhysicsBody obj2 = getObjectByVa(twoBodyConstraint.getBody2().va());
            if (obj2 != null) obj2.removeRelatedConstraint(ref);
        }
    }

    /**
     * Creates a physics explosion within the Jolt simulation.
     * <p>
     * This method must be called from the Physics Thread (e.g., inside a Tick Task).
     * It queries the broadphase for bodies within the radius and applies a linear impulse
     * based on distance (linear falloff).
     *
     * @param origin The center of the explosion (Jolt coordinates).
     * @param force  The maximum impulse force at the epicenter.
     * @param radius The radius of effect.
     */
    public void createExplosion(@NotNull Vec3 origin, float force, float radius) {
        // Захист від дивних значень
        if (force <= 0f || radius <= 0f) {
            return;
        }

        force *= 500; // Масштабування сили для кращого відчуття

        // У фізичному потоці бажано брати NoLock-версію інтерфейсу тіл
        BodyInterface bodyInterface = physicsSystem.getBodyInterfaceNoLock();
        ConstBroadPhaseQuery broadPhase = physicsSystem.getBroadPhaseQuery();

        float radiusSq = radius * radius;

        // Колектор, який збирає ID усіх тіл, чия AABB потрапила в сферу
        try (AllHitCollideShapeBodyCollector collector =
                     new AllHitCollideShapeBodyCollector()) {

            // Шукаємо всі тіла, чиї bounding box-и перетинають сферу вибуху
            // (broadphase працює тільки з AABB, це норм для вибуху)
            broadPhase.collideSphere(origin, radius, collector);

            int[] hits = collector.getHits();
            if (hits == null || hits.length == 0) {
                return;
            }

            for (int bodyId : hits) {
                // Пропускаємо статичні / кінематичні тіла – вибух не повинен їх рухати
                EMotionType motionType = bodyInterface.getMotionType(bodyId);
                if (motionType != EMotionType.Dynamic) {
                    continue;
                }

                // За бажанням можна ще відкинути сенсори
                if (bodyInterface.isSensor(bodyId)) {
                    continue;
                }

                // Позиція центру маси тіла (RVec3 – подвійна точність, система координат Jolt)
                RVec3 com = bodyInterface.getCenterOfMassPosition(bodyId);

                // Вектор від епіцентру до тіла
                float dx = com.x() - origin.getX();
                float dy = com.y() - origin.getY();
                float dz = com.z() - origin.getZ();

                Vec3 offset = new Vec3(dx, dy, dz);
                float distSq = offset.lengthSq();

                // Якщо занадто близько до нуля (щоб уникнути ділення на 0) або раптом вилізло за радіус
                if (distSq <= 1.0e-6f || distSq > radiusSq) {
                    continue;
                }

                float dist = (float) Math.sqrt(distSq);

                // Лінійне затухання: dist = 0 -> 1, dist = radius -> 0
                float factor = 1.0f - (dist / radius);
                if (factor <= 0f) {
                    continue;
                }

                // Нормалізуємо напрямок та масштабуємо за силою
                Vec3 impulse = offset.normalized();
                impulse.scaleInPlace(force * factor);

                // Активуємо тіло (на випадок якщо воно «спить»)
                bodyInterface.activateBody(bodyId);

                // Імпульс у центр мас
                bodyInterface.addImpulse(bodyId, impulse);
            }
        }
    }

    // --- Helpers ---
    private void addFloorPlane(WorldsConfig.FloorPlaneSettings floorSettings) {
        BodyInterface bi = physicsSystem.getBodyInterface();
        WorldsConfig.WorldSizeSettings sizeSettings = this.settings.size();
        Vec3 min = sizeSettings.min();
        Vec3 max = sizeSettings.max();

        float sizeX = Math.abs(max.getX() - min.getX()) * 0.5f;
        float sizeZ = Math.abs(max.getZ() - min.getZ()) * 0.5f;
        float halfExtent = Math.max(sizeX, sizeZ);

        double centerX = min.getX() + sizeX;
        double centerZ = min.getZ() + sizeZ;

        ConstPlane plane = new Plane(Vec3.sAxisY(), 0.0f);
        ConstShape floorShape = new com.github.stephengold.joltjni.PlaneShape(plane, null, halfExtent);

        RVec3 position = new RVec3(centerX, floorSettings.yLevel(), centerZ);

        BodyCreationSettings bcs = new BodyCreationSettings();
        bcs.setPosition(position);
        bcs.setMotionType(EMotionType.Static);
        bcs.setObjectLayer(PhysicsLayers.OBJ_STATIC);
        bcs.setShape(floorShape);
        bcs.setFriction(1.0f);
        bcs.setRestitution(0.0f);

        Body floor = bi.createBody(bcs);
        bi.addBody(floor, EActivation.DontActivate);
        staticBodies.add(floor);
    }

    public record RaycastResult(Long va, RVec3 hitPos) {}

    public List<RaycastResult> raycastEntity(@NotNull Location startPoint, @NotNull Vector direction, double maxDistance) {
        Vector endOffset = direction.clone().normalize().multiply(maxDistance);
        Vector endPoint = startPoint.clone().add(endOffset).toVector();

        AllHitRayCastBodyCollector collector = new AllHitRayCastBodyCollector();

        getPhysicsSystem().getBroadPhaseQuery().castRay(
                new RayCast(ConvertUtils.toVec3(startPoint), ConvertUtils.toVec3(endOffset)),
                collector
        );

        List<RaycastResult> results = new ArrayList<>();

        for (BroadPhaseCastResult hit : collector.getHits()) {
            ConstBody body = getBodyById(hit.getBodyId());
            if (body == null) continue;

            AbstractPhysicsBody obj = getObjectByVa(body.targetVa());
            if (obj != null) {
                Vector hitPos0 = MiscUtils.lerpVec(startPoint.toVector(), endPoint, hit.getFraction());
                RVec3 hitPos = ConvertUtils.toRVec3(hitPos0);
                results.add(new RaycastResult(body.targetVa(), hitPos));
            }
        }
        return results;
    }
}