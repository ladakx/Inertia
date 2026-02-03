package com.ladakx.inertia.api.service;

import com.ladakx.inertia.common.metrics.RollingAverage;
import com.ladakx.inertia.physics.world.loop.LoopTickListener;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicInteger;

public class PhysicsMetricsService implements LoopTickListener {
    private final RollingAverage mspt1s = new RollingAverage(20);
    private final RollingAverage mspt5s = new RollingAverage(100);
    private final RollingAverage mspt1m = new RollingAverage(1200);

    private final AtomicInteger activeBodyCount = new AtomicInteger(0);
    private final AtomicInteger totalBodyCount = new AtomicInteger(0); // Всього тіл (активні + сплячі)
    private final AtomicInteger staticBodyCount = new AtomicInteger(0);
    private final AtomicInteger maxBodyLimit = new AtomicInteger(0); // Ліміт з конфігу

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
}