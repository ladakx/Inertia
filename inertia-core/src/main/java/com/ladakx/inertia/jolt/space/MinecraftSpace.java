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
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.config.WorldsConfig;
import com.ladakx.inertia.jolt.PhysicsLayers;
import com.ladakx.inertia.jolt.object.AbstractPhysicsObject;
import com.ladakx.inertia.jolt.object.MinecraftPhysicsObject;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MinecraftSpace implements AutoCloseable {

    private final String worldName;
    private final World worldBukkit;
    private final WorldsConfig.WorldProfile settings; // Додано посилання на налаштування
    private final PhysicsSystem physicsSystem;

    // Посилання на глобальні ресурси
    private final JobSystem jobSystem;
    private final TempAllocator tempAllocator;

    // Екзек'ютор для фізичного циклу
    private final ScheduledExecutorService tickExecutor;
    private final AtomicBoolean isActive = new AtomicBoolean(true);
    // Прапорець для запобігання переповнення черги основного потоку
    private final AtomicBoolean isRenderScheduled = new AtomicBoolean(false);

    // Списки об'єктів у цьому просторі
    private final @NotNull List<AbstractPhysicsObject> objects = new CopyOnWriteArrayList<>();
    private final @NotNull Map<Long, AbstractPhysicsObject> objectMap = new ConcurrentHashMap<>();

    public MinecraftSpace(World world, WorldsConfig.WorldProfile settings, JobSystem jobSystem, TempAllocator tempAllocator) {
        this.worldBukkit = world;
        this.worldName = world.getName();
        this.settings = settings;
        this.jobSystem = jobSystem;
        this.tempAllocator = tempAllocator;

        // --- Налаштування шарів (Layers) ---
        ObjectLayerPairFilterTable ovoFilter = new ObjectLayerPairFilterTable(PhysicsLayers.NUM_OBJ_LAYERS);
        // Enable collisions between 2 moving bodies:
        ovoFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_MOVING);
        // Enable collisions between a moving body and a non-moving one:
        ovoFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_STATIC);
        // Disable collisions between 2 non-moving bodies:
        ovoFilter.disableCollision(PhysicsLayers.OBJ_STATIC, PhysicsLayers.OBJ_STATIC);

        // Map both object layers to broadphase layer 0:
        BroadPhaseLayerInterfaceTable layerMap = new BroadPhaseLayerInterfaceTable(PhysicsLayers.NUM_OBJ_LAYERS, PhysicsLayers.NUM_BP_LAYERS);
        layerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_MOVING, 0);
        layerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_STATIC, 0);

        // Rules for colliding object layers with broadphase layers:
        ObjectVsBroadPhaseLayerFilter ovbFilter = new ObjectVsBroadPhaseLayerFilterTable(layerMap, PhysicsLayers.NUM_BP_LAYERS, ovoFilter, PhysicsLayers.NUM_OBJ_LAYERS);



        // --- Ініціалізація системи ---
        this.physicsSystem = new PhysicsSystem();

        // Використовуємо велику константу, оскільки maxBodies не було у WorldSettings
        final int MAX_BODIES = settings.maxBodies();

        this.physicsSystem.init(
                MAX_BODIES,
                0, // Max bodies with correct world transform (usually 0)
                20_480, // Max contacts
                65_536, // Max body pairs
                layerMap,
                ovbFilter,
                ovoFilter
        );

        // Використовуємо Gravity з WorldSettings
        this.physicsSystem.setGravity(this.settings.gravity());
        this.physicsSystem.optimizeBroadPhase();

        // Створення окремого потоку для цього світу
        this.tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "inertia-space-" + worldName);
            t.setDaemon(true);
            return t;
        });

        if (this.settings.floorPlane().enabled()) {
            addFloorPlane(this.settings.floorPlane());
        }

        // Використовуємо tickRate з WorldSettings
        startSimulationLoop(this.settings.tickRate());
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
                // 1. Крок фізичної симуляції (Jolt Thread)
                int errors = physicsSystem.update(deltaTime, this.settings.collisionSteps(), tempAllocator, jobSystem);

                if (errors != EPhysicsUpdateError.None) {
                    InertiaLogger.warn("Physics error in world " + worldName + ": " + errors);
                }

                // 2. Логіка синхронізації з Bukkit (Main Thread)
                // Використовуємо compareAndSet, щоб не створювати нову задачу, якщо попередня ще не виконалась.
                // Це захищає Main Thread від перевантаження, якщо фізика працює швидше за сервер.
                if (!isRenderScheduled.getAndSet(true)) {
                    Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
                        try {
                            // Перевірка активності потрібна, бо світ міг закритися поки задача чекала в черзі
                            if (!isActive.get()) return;

                            // Оновлення візуальних позицій
                            for (AbstractPhysicsObject obj : objects) {
                                // obj.update() викликає методи Bukkit API (teleport/setTransformation),
                                // тому це безпечно робити ТІЛЬКИ тут.
                                obj.update();
                            }
                        } catch (Exception e) {
                            InertiaLogger.error("Error updating visuals for world " + worldName, e);
                        } finally {
                            // Звільняємо прапорець, дозволяючи планувати наступне оновлення
                            isRenderScheduled.set(false);
                        }
                    });
                }

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

    public WorldsConfig.WorldProfile getSettings() {
        return settings;
    }

    @Override
    public void close() {
        if (isActive.compareAndSet(true, false)) {
            InertiaLogger.info("Closing physics space for world: " + worldName);

            removeAllObjects();

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

    private void addFloorPlane(WorldsConfig.FloorPlaneSettings settings) {
        InertiaLogger.info("Adding floor plane at Y=" + settings.yLevel() + " in world [" + worldName+"]");
        BodyInterface bi = physicsSystem.getBodyInterface();

        float groundY = settings.yLevel();
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

    public @NotNull List<AbstractPhysicsObject> getObjects() {
        return objects;
    }

    public @Nullable ConstBody getBodyById(int id) {
        return new BodyLockRead(physicsSystem.getBodyLockInterfaceNoLock(), id).getBody();
    }

    /**
     * Видаляє всі динамічні об'єкти з цього світу.
     * Статичні об'єкти (як підлога) залишаються, якщо вони не є частиною списку objects.
     */
    public void removeAllObjects() {
        // Створюємо копію списку, щоб уникнути ConcurrentModificationException під час ітерації,
        // хоча CopyOnWriteArrayList дозволяє ітерацію, destroy() змінює колекцію.
        List<AbstractPhysicsObject> snapshot = new ArrayList<>(objects);

        int count = 0;
        for (AbstractPhysicsObject obj : snapshot) {
            if (obj instanceof MinecraftPhysicsObject mcObj) {
                mcObj.destroy();
                count++;
            }
        }

        InertiaLogger.info("Cleared " + count + " physics objects from world " + worldName);
    }

    public void addObject(AbstractPhysicsObject object) {
        objects.add(object);
        objectMap.put(object.getBody().va(), object);
    }
    public void removeObject(AbstractPhysicsObject object) {
        objects.remove(object);
        objectMap.remove(object.getBody().va());
    }

    public void addConstraint(Constraint constraint) {
        physicsSystem.addConstraint(constraint);
        if (constraint instanceof TwoBodyConstraint twoBodyConstraint) {
            AbstractPhysicsObject obj1 = getObjectByVa(twoBodyConstraint.getBody1().va());
            TwoBodyConstraintRef ref = twoBodyConstraint.toRef();
            if (obj1 != null) obj1.addRelatedConstraint(ref);
            AbstractPhysicsObject obj2 = getObjectByVa(twoBodyConstraint.getBody2().va());
            if (obj2 != null) obj2.addRelatedConstraint(ref);
        }
    }

    public void removeConstraint(Constraint constraint) {
        physicsSystem.removeConstraint(constraint);
        if (constraint instanceof TwoBodyConstraint twoBodyConstraint) {
            TwoBodyConstraintRef ref = twoBodyConstraint.toRef();
            AbstractPhysicsObject obj1 = getObjectByVa(twoBodyConstraint.getBody1().va());
            if (obj1 != null) obj1.removeRelatedConstraint(ref);
            AbstractPhysicsObject obj2 = getObjectByVa(twoBodyConstraint.getBody2().va());
            if (obj2 != null) obj2.removeRelatedConstraint(ref);
        }
    }

    public @Nullable AbstractPhysicsObject getObjectByVa(Long va) {
        return objectMap.get(va);
    }
}