package com.ladakx.inertia.core.physics.jolt;

import com.github.stephengold.joltjni.*; // Імпортуємо все необхідне
import com.github.stephengold.joltjni.readonly.ConstBroadPhaseLayerInterface;
import com.github.stephengold.joltjni.readonly.ConstObjectLayerPairFilter;
import com.github.stephengold.joltjni.readonly.ConstObjectVsBroadPhaseLayerFilter;
import com.ladakx.inertia.core.InertiaPluginLogger;

/**
 * Обгортка для ініціалізації та зберігання основних систем Jolt.
 * Реалізує {@link AutoCloseable} для безпечного звільнення нативних ресурсів.
 *
 * Адаптовано на основі робочого коду `MinecraftPhysics.java`.
 */
public final class JoltPhysicsSystem implements AutoCloseable {

    private final InertiaPluginLogger logger;

    // --- Налаштування Jolt (з `MinecraftPhysics.java`) ---
    private static final int MAX_BODIES = 65536; // Збільшено (з 5000)
    private static final int NUM_BODY_MUTEXES = 0; // 0 = автовизначення
    private static final int MAX_BODY_PAIRS = 65536;
    private static final int MAX_CONTACT_CONSTRAINTS = 20480; // Збільшено (з 10240)

    // --- Налаштування JobSystem (з `MinecraftPhysics.java`) ---
    private static final int MAX_JOBS = Jolt.cMaxPhysicsJobs;
    private static final int MAX_BARRIERS = Jolt.cMaxPhysicsBarriers;

    // --- Налаштування Ша_рів (з `MinecraftPhysics.java`) ---
    public static final int OBJ_LAYER_MOVING = 0;
    public static final int OBJ_LAYER_NON_MOVING = 1;
    private static final int NUM_OBJ_LAYERS = 2;

    public static final int BP_LAYER_MOVING = 0;
    public static final int BP_LAYER_NON_MOVING = 1;
    private static final int NUM_BP_LAYERS = 2;


    // --- Нативні об'єкти Jolt ---
    private JobSystem jobSystem;
    private PhysicsSystem physicsSystem;
    private TempAllocator tempAllocator;
    private BroadPhaseLayerInterfaceTable broadPhaseLayerInterface;
    private ObjectVsBroadPhaseLayerFilter objectVsBroadPhaseLayerFilter;
    private ObjectLayerPairFilter objectLayerPairFilter;

    public JoltPhysicsSystem(InertiaPluginLogger logger) {
        this.logger = logger;
        initializeSystems();
    }

    private void initializeSystems() {
        // --- Повністю адаптована логіка ініціалізації з `MinecraftPhysics.java` ---

        // 1. Створюємо фільтр пар "Об'єкт-Об'єкт" (OVO)
        this.objectLayerPairFilter = new ObjectLayerPairFilterTable(NUM_OBJ_LAYERS);
        ((ObjectLayerPairFilterTable) this.objectLayerPairFilter).enableCollision(OBJ_LAYER_MOVING, OBJ_LAYER_MOVING);
        ((ObjectLayerPairFilterTable) this.objectLayerPairFilter).enableCollision(OBJ_LAYER_MOVING, OBJ_LAYER_NON_MOVING);
        ((ObjectLayerPairFilterTable) this.objectLayerPairFilter).disableCollision(OBJ_LAYER_NON_MOVING, OBJ_LAYER_NON_MOVING);

        // 2. Створюємо карту шарів "Об'єкт -> BroadPhase" (BP)
        this.broadPhaseLayerInterface = new BroadPhaseLayerInterfaceTable(NUM_OBJ_LAYERS, NUM_BP_LAYERS);
        this.broadPhaseLayerInterface.mapObjectToBroadPhaseLayer(OBJ_LAYER_MOVING, BP_LAYER_MOVING);
        this.broadPhaseLayerInterface.mapObjectToBroadPhaseLayer(OBJ_LAYER_NON_MOVING, BP_LAYER_NON_MOVING);

        // 3. Створюємо фільтр "Об'єкт-BroadPhase" (OVB)
        this.objectVsBroadPhaseLayerFilter = new ObjectVsBroadPhaseLayerFilterTable(
                this.broadPhaseLayerInterface,
                NUM_BP_LAYERS,
                this.objectLayerPairFilter,
                NUM_OBJ_LAYERS
        );

        // 4. Створюємо PhysicsSystem
        this.physicsSystem = new PhysicsSystem();

        // 5. Ініціалізуємо PhysicsSystem
        this.physicsSystem.init(
                MAX_BODIES,
                NUM_BODY_MUTEXES,
                MAX_BODY_PAIRS,
                MAX_CONTACT_CONSTRAINTS,
                broadPhaseLayerInterface,
                objectVsBroadPhaseLayerFilter,
                objectLayerPairFilter
        );

        // 6. Оптимізуємо BroadPhase
        this.physicsSystem.optimizeBroadPhase();

        // 7. Створюємо Тимчасовий Алокатор (використовуємо Malloc, як у прикладі)
        this.tempAllocator = new TempAllocatorMalloc();

        // 8. Створюємо JobSystem (систему потоків Jolt)
        int maxThreads = (int) (Runtime.getRuntime().availableProcessors() * 0.75);
        if (maxThreads < 1) {
            maxThreads = 1;
        }
        logger.info("Allocating " + maxThreads + " threads for Jolt JobSystem...");

        this.jobSystem = new JobSystemThreadPool(MAX_JOBS, MAX_BARRIERS, maxThreads);
    }

    /**
     * Безпечно звільняє всі нативні ресурси Jolt.
     */
    @Override
    public void close() throws Exception {
        logger.info("Closing Jolt Physics Systems...");

        // Звільняємо в зворотному порядку створення
        if (this.jobSystem != null) {
            this.jobSystem.close();
        }
        if (this.tempAllocator != null) {
            this.tempAllocator.close();
        }
        if (this.physicsSystem != null) {
            this.physicsSystem.close();
        }
        if (this.objectVsBroadPhaseLayerFilter != null) {
            this.objectVsBroadPhaseLayerFilter.close();
        }
        if (this.broadPhaseLayerInterface != null) {
            this.broadPhaseLayerInterface.close();
        }
        if (this.objectLayerPairFilter != null) {
            this.objectLayerPairFilter.close();
        }
    }

    // --- Геттери ---

    public PhysicsSystem getPhysicsSystem() {
        return physicsSystem;
    }

    public JobSystem getJobSystem() {
        return jobSystem;
    }

    public TempAllocator getTempAllocator() {
        return tempAllocator;
    }

    public ConstBroadPhaseLayerInterface getBroadPhaseLayerInterface() {
        return broadPhaseLayerInterface;
    }

    public ConstObjectVsBroadPhaseLayerFilter getObjectVsBroadPhaseLayerFilter() {
        return objectVsBroadPhaseLayerFilter;
    }

    public ConstObjectLayerPairFilter getObjectLayerPairFilter() {
        return objectLayerPairFilter;
    }
}