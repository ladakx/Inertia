package com.ladakx.inertia.api.service;

import com.ladakx.inertia.common.metrics.RollingAverage;
import com.ladakx.inertia.physics.world.loop.LoopTickListener;
import org.bukkit.Bukkit;

import com.ladakx.inertia.physics.world.managers.PhysicsTaskManager;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PhysicsMetricsService implements LoopTickListener {
    private final RollingAverage mspt1s = new RollingAverage(20);
    private final RollingAverage mspt5s = new RollingAverage(100);
    private final RollingAverage mspt1m = new RollingAverage(1200);

    private final AtomicInteger activeBodyCount = new AtomicInteger(0);
    private final AtomicInteger totalBodyCount = new AtomicInteger(0); // Всього тіл (активні + сплячі)
    private final AtomicInteger staticBodyCount = new AtomicInteger(0);
    private final AtomicInteger maxBodyLimit = new AtomicInteger(0); // Ліміт з конфігу

    private final AtomicLong oneTimeExecutionNanos = new AtomicLong(0);
    private final AtomicLong recurringExecutionNanosTotal = new AtomicLong(0);
    private final EnumMap<PhysicsTaskManager.RecurringTaskPriority, AtomicLong> recurringExecutionNanosByCategory = new EnumMap<>(PhysicsTaskManager.RecurringTaskPriority.class);
    private final EnumMap<PhysicsTaskManager.RecurringTaskPriority, AtomicInteger> recurringSkippedByCategory = new EnumMap<>(PhysicsTaskManager.RecurringTaskPriority.class);
    private final AtomicInteger oneTimeQueueDepth = new AtomicInteger(0);
    private final AtomicInteger recurringQueueDepth = new AtomicInteger(0);

    public PhysicsMetricsService() {
        for (PhysicsTaskManager.RecurringTaskPriority priority : PhysicsTaskManager.RecurringTaskPriority.values()) {
            recurringExecutionNanosByCategory.put(priority, new AtomicLong(0));
            recurringSkippedByCategory.put(priority, new AtomicInteger(0));
        }
    }

    @Override
    public void onTickStart(long tickNumber) {
    }

    @Override
    public void onTickEnd(long tickNumber, long durationNanos, int activeBodies, int totalBodies, int staticBodies, int maxBodies) {
        double ms = durationNanos / 1_000_000.0;
        mspt1s.add(ms);
        mspt5s.add(ms);
        mspt1m.add(ms);

        this.activeBodyCount.set(activeBodies);
        this.totalBodyCount.set(totalBodies);
        this.staticBodyCount.set(staticBodies);
        this.maxBodyLimit.set(maxBodies);
    }

    // Використовується в BossBar
    public double getPhysicsMspt() {
        return mspt1s.getAverage();
    }

    // Використовуються в AdminCommands
    public double getAverageMspt1s() {
        return mspt1s.getAverage();
    }

    public double getAverageMspt5s() {
        return mspt5s.getAverage();
    }

    public double getAverageMspt1m() {
        return mspt1m.getAverage();
    }

    public double getPeakMspt1s() {
        return mspt1s.getMax();
    }

    public double getServerMspt() {
        return Bukkit.getAverageTickTime();
    }

    public int getActiveBodyCount() { return activeBodyCount.get(); }
    public int getTotalBodyCount() { return totalBodyCount.get(); }
    public int getSleepingBodyCount() { return totalBodyCount.get() - activeBodyCount.get(); }
    public int getStaticBodyCount() { return staticBodyCount.get(); }
    public int getMaxBodyLimit() { return maxBodyLimit.get(); }

    public void updateTaskManagerMetrics(PhysicsTaskManager.TaskManagerMetrics metrics) {
        if (metrics == null) {
            return;
        }

        oneTimeExecutionNanos.set(metrics.oneTimeExecutionNanos());
        recurringExecutionNanosTotal.set(metrics.recurringExecutionNanosTotal());
        for (PhysicsTaskManager.RecurringTaskPriority priority : PhysicsTaskManager.RecurringTaskPriority.values()) {
            recurringExecutionNanosByCategory.get(priority).set(metrics.recurringExecutionNanos(priority));
            recurringSkippedByCategory.get(priority).set(metrics.recurringSkipped(priority));
        }
        oneTimeQueueDepth.set(metrics.oneTimeQueueDepth());
        recurringQueueDepth.set(metrics.recurringQueueDepth());
    }

    public double getOneTimeExecutionMs() {
        return oneTimeExecutionNanos.get() / 1_000_000.0;
    }

    public double getRecurringExecutionMsTotal() {
        return recurringExecutionNanosTotal.get() / 1_000_000.0;
    }

    public double getRecurringExecutionMs(PhysicsTaskManager.RecurringTaskPriority priority) {
        return recurringExecutionNanosByCategory.get(priority).get() / 1_000_000.0;
    }

    public int getRecurringSkipped(PhysicsTaskManager.RecurringTaskPriority priority) {
        return recurringSkippedByCategory.get(priority).get();
    }

    public int getOneTimeQueueDepth() {
        return oneTimeQueueDepth.get();
    }

    public int getRecurringQueueDepth() {
        return recurringQueueDepth.get();
    }
}
