package com.ladakx.inertia.rendering;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Objects;

/**
 * Tick-local network scheduler with bounded work time and per-queue telemetry.
 */
public final class RenderNetworkBudgetScheduler {

    private final ArrayDeque<Runnable> spawnQueue = new ArrayDeque<>();
    private final ArrayDeque<Runnable> visibilityQueue = new ArrayDeque<>();
    private final ArrayDeque<Runnable> metadataQueue = new ArrayDeque<>();
    private final ArrayDeque<Runnable> destroyQueue = new ArrayDeque<>();

    private long maxWorkNanosPerTick = 2_000_000L;
    private double secondaryMinScale = 0.25D;
    private double tpsSoftThreshold = 19.2D;
    private double tpsHardThreshold = 17.0D;
    private int pingSoftThresholdMs = 120;
    private int pingHardThresholdMs = 220;

    private long lastTickWorkNanos;
    private long totalDeferredTasks;
    private long lastTickDeferredTasks;
    private double lastSecondaryScale = 1.0D;

    public void applySettings(com.ladakx.inertia.configuration.dto.InertiaConfig.RenderingSettings.NetworkEntityTrackerSettings settings) {
        if (settings == null) {
            return;
        }
        this.maxWorkNanosPerTick = Math.max(100_000L, settings.maxWorkNanosPerTick);
        this.secondaryMinScale = clamp(settings.secondaryBudgetMinScale, 0.05D, 1.0D);
        this.tpsSoftThreshold = Math.max(1.0D, settings.adaptiveTpsSoftThreshold);
        this.tpsHardThreshold = Math.max(1.0D, Math.min(settings.adaptiveTpsHardThreshold, this.tpsSoftThreshold));
        this.pingSoftThresholdMs = Math.max(1, settings.adaptivePingSoftThresholdMs);
        this.pingHardThresholdMs = Math.max(this.pingSoftThresholdMs, settings.adaptivePingHardThresholdMs);
    }

    public void enqueueSpawn(Runnable task) {
        spawnQueue.addLast(Objects.requireNonNull(task, "task"));
    }

    public void enqueueVisibility(Runnable task) {
        visibilityQueue.addLast(Objects.requireNonNull(task, "task"));
    }

    public void enqueueMetadata(Runnable task) {
        metadataQueue.addLast(Objects.requireNonNull(task, "task"));
    }

    public void enqueueDestroy(Runnable task) {
        destroyQueue.addLast(Objects.requireNonNull(task, "task"));
    }

    public void runTick(Collection<? extends Player> players) {
        long tickStart = System.nanoTime();
        long effectiveBudget = maxWorkNanosPerTick;

        double secondaryScale = computeSecondaryScale(players);
        this.lastSecondaryScale = secondaryScale;

        long criticalBudget = (long) (effectiveBudget * 0.5D);
        long secondaryBudget = (long) ((effectiveBudget - criticalBudget) * secondaryScale);

        long criticalUsed = 0L;
        long secondaryUsed = 0L;

        criticalUsed += runQueue(spawnQueue, criticalBudget - criticalUsed, tickStart, effectiveBudget);
        criticalUsed += runQueue(destroyQueue, criticalBudget - criticalUsed, tickStart, effectiveBudget);

        secondaryUsed += runQueue(visibilityQueue, secondaryBudget - secondaryUsed, tickStart, effectiveBudget);
        secondaryUsed += runQueue(metadataQueue, secondaryBudget - secondaryUsed, tickStart, effectiveBudget);

        long remainingGlobal = effectiveBudget - (System.nanoTime() - tickStart);
        if (remainingGlobal > 0) {
            runQueue(spawnQueue, remainingGlobal, tickStart, effectiveBudget);
            remainingGlobal = effectiveBudget - (System.nanoTime() - tickStart);
        }
        if (remainingGlobal > 0) {
            runQueue(destroyQueue, remainingGlobal, tickStart, effectiveBudget);
        }

        this.lastTickWorkNanos = System.nanoTime() - tickStart;
        this.lastTickDeferredTasks = queueDepth();
        this.totalDeferredTasks += lastTickDeferredTasks;
    }

    private long runQueue(ArrayDeque<Runnable> queue, long queueBudget, long tickStart, long tickBudget) {
        if (queueBudget <= 0) {
            return 0L;
        }

        long used = 0L;
        while (!queue.isEmpty()) {
            long elapsedGlobal = System.nanoTime() - tickStart;
            if (elapsedGlobal >= tickBudget || used >= queueBudget) {
                break;
            }

            Runnable task = queue.pollFirst();
            if (task == null) {
                break;
            }

            long started = System.nanoTime();
            task.run();
            used += Math.max(0L, System.nanoTime() - started);
        }
        return used;
    }

    private double computeSecondaryScale(Collection<? extends Player> players) {
        double mspt = Bukkit.getAverageTickTime();
        double tps = mspt <= 0 ? 20.0D : Math.min(20.0D, 1000.0D / mspt);

        double tpsScale = normalizeDescending(tps, tpsSoftThreshold, tpsHardThreshold, secondaryMinScale);
        int avgPing = computeAveragePing(players);
        double pingScale = normalizeAscending(avgPing, pingSoftThresholdMs, pingHardThresholdMs, secondaryMinScale);

        return Math.min(tpsScale, pingScale);
    }

    private int computeAveragePing(Collection<? extends Player> players) {
        if (players == null || players.isEmpty()) {
            return 0;
        }
        long sum = 0L;
        int count = 0;
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            sum += Math.max(0, player.spigot().getPing());
            count++;
        }
        return count == 0 ? 0 : (int) (sum / count);
    }

    private static double normalizeDescending(double value, double soft, double hard, double minScale) {
        if (value >= soft) {
            return 1.0D;
        }
        if (value <= hard) {
            return minScale;
        }
        double ratio = (value - hard) / Math.max(0.000001D, (soft - hard));
        return minScale + ((1.0D - minScale) * ratio);
    }

    private static double normalizeAscending(double value, double soft, double hard, double minScale) {
        if (value <= soft) {
            return 1.0D;
        }
        if (value >= hard) {
            return minScale;
        }
        double ratio = (hard - value) / Math.max(1.0D, (hard - soft));
        return minScale + ((1.0D - minScale) * ratio);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public long getLastTickWorkNanos() {
        return lastTickWorkNanos;
    }

    public long getLastTickDeferredTasks() {
        return lastTickDeferredTasks;
    }

    public long getTotalDeferredTasks() {
        return totalDeferredTasks;
    }

    public int getSpawnQueueDepth() {
        return spawnQueue.size();
    }

    public int getVisibilityQueueDepth() {
        return visibilityQueue.size();
    }

    public int getMetadataQueueDepth() {
        return metadataQueue.size();
    }

    public int getDestroyQueueDepth() {
        return destroyQueue.size();
    }

    public int queueDepth() {
        return spawnQueue.size() + visibilityQueue.size() + metadataQueue.size() + destroyQueue.size();
    }

    public double getLastSecondaryScale() {
        return lastSecondaryScale;
    }

    public void clear() {
        spawnQueue.clear();
        visibilityQueue.clear();
        metadataQueue.clear();
        destroyQueue.clear();
        lastTickDeferredTasks = 0L;
    }
}
