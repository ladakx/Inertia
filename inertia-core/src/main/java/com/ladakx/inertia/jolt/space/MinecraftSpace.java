package com.ladakx.inertia.jolt.space;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstPlane;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.files.config.InertiaConfig.PhysicsSettings.WorldSettings; // Імпорт WorldSettings
import com.ladakx.inertia.jolt.PhysicsLayers;
import com.ladakx.inertia.jolt.object.MinecraftPhysicsObject;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MinecraftSpace implements AutoCloseable {

    private final String worldName;
    private final World worldBukkit;
    private final WorldSettings settings; // Додано посилання на налаштування
    private final PhysicsSystem physicsSystem;

    // Посилання на глобальні ресурси
    private final JobSystem jobSystem;
    private final TempAllocator tempAllocator;

    // Екзек'ютор для фізичного циклу
    private final ScheduledExecutorService tickExecutor;
    private final AtomicBoolean isActive = new AtomicBoolean(true);

    // Списки об'єктів у цьому просторі
    private final @NotNull List<MinecraftPhysicsObject> objects = new CopyOnWriteArrayList<>();
    private final @NotNull Map<Long, MinecraftPhysicsObject> objectMap = new ConcurrentHashMap<>();

    public MinecraftSpace(World world, WorldSettings settings, JobSystem jobSystem, TempAllocator tempAllocator) {
        this.worldBukkit = world;
        this.worldName = world.getName();
        this.settings = settings;
        this.jobSystem = jobSystem;
        this.tempAllocator = tempAllocator;

        // --- Налаштування шарів (Layers) ---
        ObjectLayerPairFilterTable objectLayerFilter = new ObjectLayerPairFilterTable(PhysicsLayers.NUM_OBJ_LAYERS);
        objectLayerFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_MOVING);
        objectLayerFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_STATIC);

        BroadPhaseLayerInterfaceTable bpLayerMap = new BroadPhaseLayerInterfaceTable(PhysicsLayers.NUM_OBJ_LAYERS, PhysicsLayers.NUM_BP_LAYERS);
        bpLayerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_MOVING, PhysicsLayers.BP_MOVING);
        bpLayerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_STATIC, PhysicsLayers.BP_STATIC);

        ObjectVsBroadPhaseLayerFilterTable objectVsBpFilter = new ObjectVsBroadPhaseLayerFilterTable(
                bpLayerMap, PhysicsLayers.NUM_BP_LAYERS, objectLayerFilter, PhysicsLayers.NUM_OBJ_LAYERS
        );

        // --- Ініціалізація системи ---
        this.physicsSystem = new PhysicsSystem();

        // Використовуємо велику константу, оскільки maxBodies не було у WorldSettings
        final int MAX_BODIES = settings.maxBodies;

        this.physicsSystem.init(
                MAX_BODIES,
                0, // Max bodies with correct world transform (usually 0)
                MAX_BODIES * 4, // Max contacts
                MAX_BODIES * 2, // Max body pairs
                bpLayerMap,
                objectVsBpFilter,
                objectLayerFilter
        );

        // Використовуємо Gravity з WorldSettings
        this.physicsSystem.setGravity(this.settings.gravity.x, this.settings.gravity.y, this.settings.gravity.z);
        this.physicsSystem.optimizeBroadPhase();

        // Створення окремого потоку для цього світу
        this.tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "inertia-space-" + worldName);
            t.setDaemon(true);
            return t;
        });

        if (this.settings.floorPlaneEnable) {
            addFloorPlane(this.settings);
        }

        // Використовуємо tickRate з WorldSettings
        startSimulationLoop(this.settings.tickRate);
    }

    private void startSimulationLoop(int fps) {
        if (fps <= 0) {
            InertiaLogger.warn("Physics Tick-Rate is set to " + fps + " for world " + worldName + ". Using default 20.");
            fps = 20;
        }

        long periodMicros = 1_000_000 / fps;
        float deltaTime = 1.0f / fps;

        tickExecutor.scheduleAtFixedRate(() -> {
            if (!isActive.get()) return;

            try {
                // Оновлення фізики (крок симуляції)
                int errors = physicsSystem.update(deltaTime, this.settings.collisionSteps, tempAllocator, jobSystem);

                if (errors != EPhysicsUpdateError.None) {
                    InertiaLogger.warn("Physics error in world " + worldName + ": " + errors);
                }

                // ТУТ: Логіка синхронізації (якщо потрібна)

            } catch (Exception e) {
                InertiaLogger.error("Error in physics tick for world " + worldName, e);
            }
        }, 0, periodMicros, TimeUnit.MICROSECONDS);
    }

    public PhysicsSystem getPhysicsSystem() {
        return physicsSystem;
    }

    public BodyInterface getBodyInterface() {
        return physicsSystem.getBodyInterface();
    }

    // Додаємо геттер для WorldSettings
    public WorldSettings getSettings() {
        return settings;
    }

    @Override
    public void close() {
        if (isActive.compareAndSet(true, false)) {
            InertiaLogger.info("Closing physics space for world: " + worldName);

            tickExecutor.shutdown();
            try {
                if (!tickExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    tickExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                tickExecutor.shutdownNow();
            }

            physicsSystem.destroyAllBodies();
        }
    }

    private void addFloorPlane(WorldSettings settings) {
        BodyInterface bi = physicsSystem.getBodyInterface();

        float groundY = settings.floorPlaneY;
        Vec3Arg normal = Vec3.sAxisY();
        ConstPlane plane = new Plane(normal, -groundY);
        ConstShape floorShape = new PlaneShape(plane);

        BodyCreationSettings bcs = new BodyCreationSettings();

        bcs.setMotionType(EMotionType.Static);
        bcs.setObjectLayer(PhysicsLayers.OBJ_STATIC);
        bcs.setShape(floorShape);

        Body floor = bi.createBody(bcs);
        bi.addBody(floor, EActivation.DontActivate);
    }

    public World getWorldBukkit() {
        return worldBukkit;
    }

    public @NotNull List<MinecraftPhysicsObject> getObjects() {
        return objects;
    }

    public @Nullable ConstBody getBodyById(int id) {
        return new BodyLockRead(physicsSystem.getBodyLockInterfaceNoLock(), id).getBody();
    }

    public void addObject(MinecraftPhysicsObject object) {
        objects.add(object);
        objectMap.put(object.getBody().va(), object);
    }
    public void removeObject(MinecraftPhysicsObject object) {
        objects.remove(object);
        objectMap.remove(object.getBody().va());
    }

    public void addConstraint(Constraint constraint) {
        physicsSystem.addConstraint(constraint);
        if (constraint instanceof TwoBodyConstraint twoBodyConstraint) {
            MinecraftPhysicsObject obj1 = getObjectByVa(twoBodyConstraint.getBody1().va());
            TwoBodyConstraintRef ref = twoBodyConstraint.toRef();
            if (obj1 != null) obj1.addRelatedConstraint(ref);
            MinecraftPhysicsObject obj2 = getObjectByVa(twoBodyConstraint.getBody2().va());
            if (obj2 != null) obj2.addRelatedConstraint(ref);
        }
    }

    public void removeConstraint(Constraint constraint) {
        physicsSystem.removeConstraint(constraint);
        if (constraint instanceof TwoBodyConstraint twoBodyConstraint) {
            TwoBodyConstraintRef ref = twoBodyConstraint.toRef();
            MinecraftPhysicsObject obj1 = getObjectByVa(twoBodyConstraint.getBody1().va());
            if (obj1 != null) obj1.removeRelatedConstraint(ref);
            MinecraftPhysicsObject obj2 = getObjectByVa(twoBodyConstraint.getBody2().va());
            if (obj2 != null) obj2.removeRelatedConstraint(ref);
        }
    }

    public @Nullable MinecraftPhysicsObject getObjectByVa(Long va) {
        return objectMap.get(va);
    }
}