package com.ladakx.inertia.performance.pool;

import com.ladakx.inertia.InertiaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool for handling simulation tasks.
 */
public class PhysicsThreadPool implements AutoCloseable {
    /**
     * The internal executor instance for the thread pool.
     */
    private final ScheduledThreadPoolExecutor scheduler;

    /**
     * Constructor to initialize the SimulationThreadPool.
     */
    public PhysicsThreadPool() {
        int threadCount = InertiaPlugin.getPConfig().SIMULATION.threads;
        scheduler = new ScheduledThreadPoolExecutor(
                threadCount,
                runnable -> {
                    // Create a thread with a meaningful name for debugging
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setName("PhysicsPoolThread-" + thread.getId());
                    return thread;
                },
                // Handler for rejected tasks
                (task, executor) -> InertiaPlugin.logSevere("Task rejected from pool")
        );
    }

    /**
     * Execute a task in the thread pool.
     *
     * @param task The task to execute.
     */
    public void execute(@NotNull Runnable task) {
        scheduler.execute(task);
    }

    /**
     * Graceful shutdown of the thread pool.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the internal executor instance for advanced usage.
     *
     * @return The ScheduledThreadPoolExecutor instance.
     */
    public ScheduledThreadPoolExecutor getExecutorService() {
        return scheduler;
    }

    /**
     * Method to enable try-with-resources for automatic closing of the pool.
     */
    @Override
    public void close() {
        shutdown();
    }
}