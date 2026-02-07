package com.ladakx.inertia.physics.world.managers;

import com.ladakx.inertia.common.logging.InertiaLogger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PhysicsTaskManager {

    private final Map<UUID, Runnable> tickTasks = new ConcurrentHashMap<>();
    private final Queue<Runnable> oneTimeTasks = new ConcurrentLinkedQueue<>();
    private static final int MAX_ONE_TIME_TASKS_PER_TICK = 50;
    private static final long ONE_TIME_TASK_BUDGET_NANOS = 4_000_000L; // ~4ms

    public UUID addTickTask(@NotNull Runnable task) {
        UUID id = UUID.randomUUID();
        tickTasks.put(id, task);
        return id;
    }

    public void removeTickTask(UUID id) {
        if (id != null) tickTasks.remove(id);
    }

    public void schedule(@NotNull Runnable task) {
        oneTimeTasks.add(task);
    }

    public void runAll() {
        // 1. One-time tasks
        long start = System.nanoTime();
        Runnable task;
        int processed = 0;
        while (processed < MAX_ONE_TIME_TASKS_PER_TICK && (task = oneTimeTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable e) {
                InertiaLogger.error("Error executing one-time physics task", e);
            }
            processed++;
            if (System.nanoTime() - start >= ONE_TIME_TASK_BUDGET_NANOS) {
                break;
            }
        }

        // 2. Recurring tick tasks
        for (Runnable tickTask : tickTasks.values()) {
            try {
                tickTask.run();
            } catch (Throwable e) {
                InertiaLogger.error("Error executing recurring physics task", e);
            }
        }
    }
}
