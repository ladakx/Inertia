package com.ladakx.inertia.rendering;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

/**
 * Tick-local network scheduler with bounded work time and per-queue telemetry.
 */
public final class RenderNetworkBudgetScheduler {

    private final ArrayDeque<ScheduledTask> spawnQueue = new ArrayDeque<>();
    private final ArrayDeque<ScheduledTask> visibilityQueue = new ArrayDeque<>();
    private final ArrayDeque<ScheduledTask> metadataQueue = new ArrayDeque<>();
    private final ArrayDeque<ScheduledTask> destroyQueue = new ArrayDeque<>();

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
    private long coalescedTaskCount;

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
        enqueueSpawn(task, null, -1L);
    }

    public void enqueueSpawn(Runnable task, Integer visualId, long tokenVersion) {
        spawnQueue.addLast(new ScheduledTask(Objects.requireNonNull(task, "task"), System.nanoTime(), visualId, tokenVersion));
    }

    public void enqueueVisibility(Runnable task) {
        visibilityQueue.addLast(new ScheduledTask(Objects.requireNonNull(task, "task"), System.nanoTime()));
    }

    public void enqueueMetadata(Runnable task) {
        enqueueMetadata(task, null, -1L);
    }

    public void enqueueMetadata(Runnable task, Integer visualId, long tokenVersion) {
        metadataQueue.addLast(new ScheduledTask(Objects.requireNonNull(task, "task"), System.nanoTime(), visualId, tokenVersion));
    }

    public void enqueueDestroy(Runnable task) {
        destroyQueue.addLast(new ScheduledTask(Objects.requireNonNull(task, "task"), System.nanoTime(), null, -1L));
    }

    public void enqueueMetadataCoalesced(int visualId, Runnable task) {
        Objects.requireNonNull(task, "task");

        Iterator<ScheduledTask> iterator = metadataQueue.descendingIterator();
        while (iterator.hasNext()) {
            ScheduledTask queued = iterator.next();
            if (queued.visualId != null && queued.visualId == visualId) {
                iterator.remove();
                coalescedTaskCount++;
                break;
            }
        }

        metadataQueue.addLast(new ScheduledTask(task, System.nanoTime(), visualId, -1L));
    }

    public void invalidateVisual(int visualId, long activeTokenVersion) {
        removeStaleForVisual(spawnQueue, visualId, activeTokenVersion);
        removeStaleForVisual(metadataQueue, visualId, activeTokenVersion);
    }

    public void invalidateVisuals(int[] visualIds, java.util.function.IntToLongFunction activeTokenProvider) {
        if (visualIds == null || visualIds.length == 0 || activeTokenProvider == null) {
            return;
        }
        for (int visualId : visualIds) {
            long activeToken = activeTokenProvider.applyAsLong(visualId);
            invalidateVisual(visualId, activeToken);
        }
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

    private long runQueue(ArrayDeque<ScheduledTask> queue, long queueBudget, long tickStart, long tickBudget) {
        if (queueBudget <= 0) {
            return 0L;
        }

        long used = 0L;
        while (!queue.isEmpty()) {
            long elapsedGlobal = System.nanoTime() - tickStart;
            if (elapsedGlobal >= tickBudget || used >= queueBudget) {
                break;
            }

            ScheduledTask task = queue.pollFirst();
            if (task == null) {
                break;
            }

            long started = System.nanoTime();
            task.runnable.run();
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

    public long getOldestQueueAgeMillis() {
        long now = System.nanoTime();
        long oldestNanos = oldestTaskNanos(now, spawnQueue);
        oldestNanos = Math.max(oldestNanos, oldestTaskNanos(now, visibilityQueue));
        oldestNanos = Math.max(oldestNanos, oldestTaskNanos(now, metadataQueue));
        oldestNanos = Math.max(oldestNanos, oldestTaskNanos(now, destroyQueue));
        return oldestNanos / 1_000_000L;
    }

    public long getDestroyQueueOldestAgeMillis() {
        return oldestTaskNanos(System.nanoTime(), destroyQueue) / 1_000_000L;
    }

    public long getCoalescedTaskCount() {
        return coalescedTaskCount;
    }

    private long oldestTaskNanos(long now, ArrayDeque<ScheduledTask> queue) {
        ScheduledTask first = queue.peekFirst();
        if (first == null) {
            return 0L;
        }
        return Math.max(0L, now - first.enqueuedAtNanos);
    }

    private void removeStaleForVisual(ArrayDeque<ScheduledTask> queue, int visualId, long activeTokenVersion) {
        Iterator<ScheduledTask> iterator = queue.iterator();
        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next();
            if (task.visualId == null || task.visualId != visualId) {
                continue;
            }
            if (task.tokenVersion >= 0L && task.tokenVersion != activeTokenVersion) {
                iterator.remove();
            }
        }
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

    private static final class ScheduledTask {
        private final Runnable runnable;
        private final long enqueuedAtNanos;
        private final Integer visualId;
        private final long tokenVersion;

        private ScheduledTask(Runnable runnable, long enqueuedAtNanos) {
            this(runnable, enqueuedAtNanos, null, -1L);
        }

        private ScheduledTask(Runnable runnable, long enqueuedAtNanos, Integer visualId, long tokenVersion) {
            this.runnable = runnable;
            this.enqueuedAtNanos = enqueuedAtNanos;
            this.visualId = visualId;
            this.tokenVersion = tokenVersion;
        }
    }
}
