package com.ladakx.inertia.jolt;

import com.github.stephengold.joltjni.*;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.config.ConfigManager;
import com.ladakx.inertia.jolt.shape.JShapeFactory;
import com.ladakx.inertia.utils.mesh.BlockBenchMeshProvider;

public class JoltManager {

    private static JoltManager instance;

    private final InertiaPlugin plugin;

    // Глобальні ресурси, спільні для всіх світів
    private final JobSystem jobSystem;
    private final TempAllocator tempAllocator;

    private JoltManager(InertiaPlugin plugin) {
        InertiaLogger.info("Initializing Jolt Physics native library...");
        this.plugin = plugin;

        // 1. Ініціалізація пам'яті
        this.tempAllocator = new TempAllocatorMalloc();

        // 2. Налаштування потоків
        // Залишаємо 1 ядро для головного потоку сервера (Main Thread), решту - для фізики
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int numThreads = Math.max(ConfigManager.getInstance().getInertiaConfig().PHYSICS.workerThreads, availableProcessors - 1);

        this.jobSystem = new JobSystemThreadPool(
                Jolt.cMaxPhysicsJobs,
                Jolt.cMaxPhysicsBarriers,
                numThreads
        );

        InertiaLogger.info("Jolt initialized with " + numThreads + " worker threads.");
    }

    public static void init(InertiaPlugin plugin) {
        if (instance == null) {
            instance = new JoltManager(plugin);
            JShapeFactory.setMeshProvider(new BlockBenchMeshProvider(plugin));
        }
    }

    public static JoltManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("JoltManager not initialized! Call init() first.");
        }
        return instance;
    }

    public void shutdown() {
        InertiaLogger.info("Shutting down Jolt Global Manager...");
        if (jobSystem != null) jobSystem.close();
        if (tempAllocator != null) tempAllocator.close();
        Jolt.destroyFactory();
        instance = null;
    }

    public JobSystem getJobSystem() {
        return jobSystem;
    }

    public TempAllocator getTempAllocator() {
        return tempAllocator;
    }
}