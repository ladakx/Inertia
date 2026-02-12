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

        // OPTIMIZATION: Use TempAllocatorImpl (Stack-based) instead of Malloc.
        // 32MB buffer is usually sufficient for complex scenes.
        // In a full implementation, this should come from config, but we hardcode a safe default for now.
        int tempAllocatorSize = 32 * 1024 * 1024;
        this.tempAllocator = new TempAllocatorImpl(tempAllocatorSize);

        int numThreads = configurationService.getInertiaConfig().PERFORMANCE.THREADING.physics.worldThreads;

        this.jobSystem = new JobSystemThreadPool(
                Jolt.cMaxPhysicsJobs,
                Jolt.cMaxPhysicsBarriers,
                numThreads
        );
        InertiaLogger.info("Jolt initialized with " + numThreads + " worker threads and " + (tempAllocatorSize / 1024 / 1024) + "MB temp memory.");
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