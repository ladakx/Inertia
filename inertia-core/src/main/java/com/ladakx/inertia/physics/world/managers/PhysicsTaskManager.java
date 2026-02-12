package com.ladakx.inertia.physics.world.managers;

import com.ladakx.inertia.common.logging.InertiaLogger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class PhysicsTaskManager {

    public enum RecurringTaskPriority {
        CRITICAL,
        NORMAL,
        BACKGROUND
    }

    public static class TaskManagerMetrics {
        private final long oneTimeExecutionNanos;
        private final long recurringExecutionNanosTotal;
        private final Map<RecurringTaskPriority, Long> recurringExecutionNanosByCategory;
        private final Map<RecurringTaskPriority, Integer> recurringSkippedByCategory;
        private final int oneTimeQueueDepth;
        private final int recurringQueueDepth;

        public TaskManagerMetrics(long oneTimeExecutionNanos,
                                  long recurringExecutionNanosTotal,
                                  Map<RecurringTaskPriority, Long> recurringExecutionNanosByCategory,
                                  Map<RecurringTaskPriority, Integer> recurringSkippedByCategory,
                                  int oneTimeQueueDepth,
                                  int recurringQueueDepth) {
            this.oneTimeExecutionNanos = oneTimeExecutionNanos;
            this.recurringExecutionNanosTotal = recurringExecutionNanosTotal;
            this.recurringExecutionNanosByCategory = Map.copyOf(recurringExecutionNanosByCategory);
            this.recurringSkippedByCategory = Map.copyOf(recurringSkippedByCategory);
            this.oneTimeQueueDepth = oneTimeQueueDepth;
            this.recurringQueueDepth = recurringQueueDepth;
        }

        public long oneTimeExecutionNanos() {
            return oneTimeExecutionNanos;
        }

        public long recurringExecutionNanosTotal() {
            return recurringExecutionNanosTotal;
        }

        public long recurringExecutionNanos(RecurringTaskPriority priority) {
            return recurringExecutionNanosByCategory.getOrDefault(priority, 0L);
        }

        public int recurringSkipped(RecurringTaskPriority priority) {
            return recurringSkippedByCategory.getOrDefault(priority, 0);
        }

        public int oneTimeQueueDepth() {
            return oneTimeQueueDepth;
        }

        public int recurringQueueDepth() {
            return recurringQueueDepth;
        }
    }

    private static class RecurringTaskGroup {
        private final Map<UUID, Runnable> tasks = new ConcurrentHashMap<>();
        private final AtomicInteger roundRobinCursor = new AtomicInteger(0);
    }

    private final Queue<Runnable> oneTimeTasks = new ConcurrentLinkedQueue<>();
    private final EnumMap<RecurringTaskPriority, RecurringTaskGroup> recurringTaskGroups = new EnumMap<>(RecurringTaskPriority.class);

    private volatile int maxOneTimeTasksPerTick = 50;
    private volatile long oneTimeTaskBudgetNanos = 4_000_000L;
    private volatile long recurringTaskBudgetNanos = 3_000_000L;

    public PhysicsTaskManager() {
        for (RecurringTaskPriority priority : RecurringTaskPriority.values()) {
            recurringTaskGroups.put(priority, new RecurringTaskGroup());
        }
    }

    public void updateLimits(int maxOneTimeTasksPerTick, long oneTimeTaskBudgetNanos, long recurringTaskBudgetNanos) {
        this.maxOneTimeTasksPerTick = Math.max(1, maxOneTimeTasksPerTick);
        this.oneTimeTaskBudgetNanos = Math.max(100_000L, oneTimeTaskBudgetNanos);
        this.recurringTaskBudgetNanos = Math.max(100_000L, recurringTaskBudgetNanos);
    }

    public void updateBudget(long oneTimeTaskBudgetNanos, long recurringTaskBudgetNanos) {
        this.oneTimeTaskBudgetNanos = Math.max(100_000L, oneTimeTaskBudgetNanos);
        this.recurringTaskBudgetNanos = Math.max(100_000L, recurringTaskBudgetNanos);
    }

    public UUID addTickTask(@NotNull Runnable task) {
        return addTickTask(task, RecurringTaskPriority.NORMAL);
    }

    public UUID addTickTask(@NotNull Runnable task, @NotNull RecurringTaskPriority priority) {
        UUID id = UUID.randomUUID();
        recurringTaskGroups.get(priority).tasks.put(id, task);
        return id;
    }

    public void removeTickTask(UUID id) {
        if (id == null) {
            return;
        }
        for (RecurringTaskGroup group : recurringTaskGroups.values()) {
            if (group.tasks.remove(id) != null) {
                break;
            }
        }
    }

    public void schedule(@NotNull Runnable task) {
        oneTimeTasks.add(task);
    }

    public TaskManagerMetrics runAll() {
        // 1. One-time tasks
        long oneTimeStart = System.nanoTime();
        Runnable task;
        int processed = 0;
        while (processed < maxOneTimeTasksPerTick && (task = oneTimeTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable e) {
                InertiaLogger.error("Error executing one-time physics task", e);
            }
            processed++;
            if (System.nanoTime() - oneTimeStart >= oneTimeTaskBudgetNanos) {
                break;
            }
        }
        long oneTimeExecutionNanos = System.nanoTime() - oneTimeStart;

        // 2. Recurring tick tasks with budget and priorities
        long recurringStart = System.nanoTime();
        EnumMap<RecurringTaskPriority, Long> recurringExecutionNanosByCategory = new EnumMap<>(RecurringTaskPriority.class);
        EnumMap<RecurringTaskPriority, Integer> recurringSkippedByCategory = new EnumMap<>(RecurringTaskPriority.class);
        for (RecurringTaskPriority priority : RecurringTaskPriority.values()) {
            recurringExecutionNanosByCategory.put(priority, 0L);
            recurringSkippedByCategory.put(priority, 0);
        }

        executeRecurringCategory(RecurringTaskPriority.CRITICAL, recurringStart, recurringExecutionNanosByCategory, recurringSkippedByCategory, false);
        executeRecurringCategory(RecurringTaskPriority.NORMAL, recurringStart, recurringExecutionNanosByCategory, recurringSkippedByCategory, false);
        executeRecurringCategory(RecurringTaskPriority.BACKGROUND, recurringStart, recurringExecutionNanosByCategory, recurringSkippedByCategory, true);

        long recurringExecutionNanosTotal = System.nanoTime() - recurringStart;

        return new TaskManagerMetrics(
                oneTimeExecutionNanos,
                recurringExecutionNanosTotal,
                recurringExecutionNanosByCategory,
                recurringSkippedByCategory,
                oneTimeTasks.size(),
                recurringQueueDepth()
        );
    }

    private void executeRecurringCategory(RecurringTaskPriority priority,
                                          long recurringStart,
                                          EnumMap<RecurringTaskPriority, Long> recurringExecutionNanosByCategory,
                                          EnumMap<RecurringTaskPriority, Integer> recurringSkippedByCategory,
                                          boolean allowBudgetSkipping) {
        RecurringTaskGroup group = recurringTaskGroups.get(priority);
        List<Runnable> snapshot = new ArrayList<>(group.tasks.values());
        int size = snapshot.size();
        if (size == 0) {
            return;
        }

        int startIndex = Math.floorMod(group.roundRobinCursor.get(), size);
        int executed = 0;

        for (int i = 0; i < size; i++) {
            if (allowBudgetSkipping && System.nanoTime() - recurringStart >= recurringTaskBudgetNanos) {
                recurringSkippedByCategory.put(priority, recurringSkippedByCategory.get(priority) + (size - executed));
                break;
            }

            int index = (startIndex + i) % size;
            Runnable tickTask = snapshot.get(index);
            long categoryTaskStart = System.nanoTime();
            try {
                tickTask.run();
            } catch (Throwable e) {
                InertiaLogger.error("Error executing recurring physics task", e);
            }
            long elapsed = System.nanoTime() - categoryTaskStart;
            recurringExecutionNanosByCategory.put(priority, recurringExecutionNanosByCategory.get(priority) + elapsed);
            executed++;
        }

        if (allowBudgetSkipping) {
            int executedFinal = executed;
            group.roundRobinCursor.updateAndGet(current -> (current + executedFinal) % Math.max(size, 1));
        } else {
            group.roundRobinCursor.set(0);
        }
    }

    private int recurringQueueDepth() {
        int depth = 0;
        for (RecurringTaskGroup group : recurringTaskGroups.values()) {
            depth += group.tasks.size();
        }
        return depth;
    }
}
