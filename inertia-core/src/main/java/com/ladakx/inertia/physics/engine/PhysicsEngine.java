package com.ladakx.inertia.physics.engine;

import com.github.stephengold.joltjni.*;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.ConfigurationService;

public class PhysicsEngine {

    private final InertiaPlugin plugin;
    private final JobSystem jobSystem;
    private final TempAllocator tempAllocator;

    public PhysicsEngine(InertiaPlugin plugin, ConfigurationService configurationService) {
        InertiaLogger.info("Initializing Jolt Physics native library...");
        this.plugin = plugin;

        this.tempAllocator = new TempAllocatorMalloc();

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int numThreads = Math.max(configurationService.getInertiaConfig().PHYSICS.workerThreads, availableProcessors - 1);

        this.jobSystem = new JobSystemThreadPool(
                Jolt.cMaxPhysicsJobs,
                Jolt.cMaxPhysicsBarriers,
                numThreads
        );

        InertiaLogger.info("Jolt initialized with " + numThreads + " worker threads.");
    }

    public void shutdown() {
        InertiaLogger.info("Shutting down Jolt Global Manager...");
        if (jobSystem != null) jobSystem.close();
        if (tempAllocator != null) tempAllocator.close();
        Jolt.destroyFactory();
    }

    public JobSystem getJobSystem() { return jobSystem; }
    public TempAllocator getTempAllocator() { return tempAllocator; }
}