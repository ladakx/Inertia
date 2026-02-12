package com.ladakx.inertia.physics.world.loop;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import com.ladakx.inertia.physics.world.snapshot.SnapshotPool;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.locks.LockSupport;

public class PhysicsLoop {
    private static final int SERVER_SYNC_TPS = 20;
    private static final int MIN_SNAPSHOTS_PER_SYNC_TICK = 5;
    private static final int MAX_SNAPSHOTS_PER_SYNC_TICK = 10;
    private static final int MIN_OVERLOADED_WORLD_TPS = 10;
    private static final int MAX_FIFO_SNAPSHOTS = 2;

    public enum SnapshotMode {
        FIFO,
        LATEST
    }

    private final String name;
    private final Thread tickThread;
    private final AtomicBoolean isActive = new AtomicBoolean(false);

    private final AtomicReference<PhysicsSnapshot> latestSnapshot = new AtomicReference<>();
    private final Object fifoLock = new Object();
    private final ArrayDeque<PhysicsSnapshot> fifoSnapshots = new ArrayDeque<>(MAX_FIFO_SNAPSHOTS);
    private final SnapshotPool snapshotPool;
    private volatile SnapshotMode snapshotMode;

    private BukkitTask syncTask;
    private final Runnable physicsStep;
    private final Supplier<PhysicsSnapshot> snapshotProducer;
    private final Consumer<PhysicsSnapshot> snapshotConsumer;
    private final List<LoopTickListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicLong tickCounter = new AtomicLong(0);
    private final Supplier<Integer> activeBodyCounter;
    private final Supplier<Integer> totalBodyCounter;
    private final Supplier<Integer> staticBodyCounter;
    private final int maxBodyLimit;
    private final int targetTps;
    private final int maxSnapshotsPerSyncTick;
    private final int syncCapacityTps;

    private final AtomicLong droppedSnapshots = new AtomicLong(0);
    private final AtomicLong overwrittenSnapshots = new AtomicLong(0);

    private volatile int effectiveTargetTps;

    public PhysicsLoop(String name,
                       int tps,
                       int maxBodyLimit,
                       Runnable physicsStep,
                       Supplier<PhysicsSnapshot> snapshotProducer,
                       Consumer<PhysicsSnapshot> snapshotConsumer,
                       Supplier<Integer> activeBodyCounter,
                       Supplier<Integer> totalBodyCounter,
                       Supplier<Integer> staticBodyCounter,
                       SnapshotPool snapshotPool,
                       SnapshotMode snapshotMode) {
        this.name = name;
        if (tps <= 0) {
            InertiaLogger.warn("Invalid tick-rate (" + tps + ") for world '" + name + "'. Falling back to 20.");
            this.targetTps = 20;
        } else {
            this.targetTps = tps;
        }

        this.maxSnapshotsPerSyncTick = Math.min(
                MAX_SNAPSHOTS_PER_SYNC_TICK,
                Math.max(MIN_SNAPSHOTS_PER_SYNC_TICK, (int) Math.ceil(this.targetTps / (double) SERVER_SYNC_TPS))
        );
        this.syncCapacityTps = this.maxSnapshotsPerSyncTick * SERVER_SYNC_TPS;
        if (this.targetTps > this.syncCapacityTps) {
            InertiaLogger.warn("Physics target TPS " + this.targetTps + " for world '" + name + "' exceeds sync-task capacity "
                    + this.syncCapacityTps + " TPS (" + this.maxSnapshotsPerSyncTick + " snapshots/tick). Capping physics TPS to "
                    + this.syncCapacityTps + ".");
            this.effectiveTargetTps = this.syncCapacityTps;
        } else {
            this.effectiveTargetTps = this.targetTps;
        }

        this.maxBodyLimit = maxBodyLimit;
        this.physicsStep = physicsStep;
        this.snapshotProducer = snapshotProducer;
        this.snapshotConsumer = snapshotConsumer;
        this.activeBodyCounter = activeBodyCounter;
        this.totalBodyCounter = totalBodyCounter;
        this.staticBodyCounter = staticBodyCounter;
        this.snapshotPool = snapshotPool;
        this.snapshotMode = snapshotMode;

        this.tickThread = new Thread(this::runLoop, "inertia-loop-" + name);
        this.tickThread.setDaemon(true);
        this.tickThread.setPriority(Thread.NORM_PRIORITY + 1);
        this.tickThread.setUncaughtExceptionHandler((t, e) -> {
            InertiaLogger.error("Uncaught error in physics loop thread '" + t.getName() + "'", e);
            isActive.set(false);
        });

        start();
    }

    public void applySettings(SnapshotMode snapshotMode) {
        if (snapshotMode == null) {
            return;
        }
        this.snapshotMode = snapshotMode;
    }

    public void addListener(LoopTickListener listener) {
        if (listener != null) listeners.add(listener);
    }

    private void start() {
        isActive.set(true);
        tickThread.start();

        try {
            this.syncTask = Bukkit.getScheduler().runTaskTimer(InertiaPlugin.getInstance(), () -> {
                if (!isActive.get()) return;

                if (snapshotMode == SnapshotMode.FIFO) {
                    int processed = 0;
                    while (processed < maxSnapshotsPerSyncTick) {
                        PhysicsSnapshot snapshot = pollFifoSnapshot();
                        if (snapshot == null) {
                            break;
                        }
                        snapshotConsumer.accept(snapshot);
                        processed++;
                    }
                    return;
                }

                PhysicsSnapshot snapshot = latestSnapshot.getAndSet(null);
                if (snapshot != null) {
                    snapshotConsumer.accept(snapshot);
                }
            }, 1L, 1L);
        } catch (Throwable t) {
            InertiaLogger.error("Failed to start sync snapshot consumer for world '" + name + "'", t);
        }
    }

    private void runLoop() {
        long lastTime = System.nanoTime();

        while (isActive.get()) {
            int queueSize = getPendingSnapshotCount();
// Виправляємо умову backlog. Для FIFO ми допускаємо наявність кадрів у буфері.
// Вважаємо затором тільки якщо ми досягли ліміту буфера.
            boolean backlog;
            if (snapshotMode == SnapshotMode.FIFO) {
                // MAX_FIFO_SNAPSHOTS = 2. Якщо черга >= 2, значить мейн тред не встигає.
                backlog = queueSize >= MAX_FIFO_SNAPSHOTS;
            } else {
                // Для LATEST, якщо старий не забрали, це вже backlog, бо ми його перезапишемо.
                backlog = queueSize > 0;
            }

// Додатково: не зменшуємо TPS занадто різко, якщо це лише короткочасний пік
            int desiredTps = backlog
                    ? Math.max(MIN_OVERLOADED_WORLD_TPS, effectiveTargetTps / 2)
                    : Math.min(targetTps, syncCapacityTps);

            long nsPerTick = 1_000_000_000L / Math.max(1, effectiveTargetTps);
            long now = System.nanoTime();
            long deltaTime = now - lastTime;

            if (deltaTime >= nsPerTick) {
                lastTime = now;

                try {
                    long tick = tickCounter.incrementAndGet();
                    long startTick = System.nanoTime();

                    for (LoopTickListener listener : listeners) listener.onTickStart(tick);

                    physicsStep.run();

                    PhysicsSnapshot snapshot = snapshotProducer.get();
                    if (snapshot != null) {
                        publishSnapshot(snapshot);
                    }

                    long duration = System.nanoTime() - startTick;

                    if (!listeners.isEmpty()) {
                        int active = activeBodyCounter.get();
                        int total = totalBodyCounter.get();
                        int stat = staticBodyCounter.get();
                        long dropped = droppedSnapshots.get();
                        long overwritten = overwrittenSnapshots.get();
                        for (LoopTickListener listener : listeners) {
                            listener.onTickEnd(tick, duration, active, total, stat, maxBodyLimit, dropped, overwritten);
                        }
                    }

                } catch (Throwable e) {
                    InertiaLogger.error("Error in physics loop: " + name, e);
                    isActive.set(false);
                }
            } else {
                long waitNs = nsPerTick - deltaTime;
                if (waitNs > 1_000_000) {
                    LockSupport.parkNanos(waitNs - 500_000);
                } else {
                    Thread.onSpinWait();
                }
            }
        }
    }

    private void publishSnapshot(PhysicsSnapshot snapshot) {
        if (snapshotMode == SnapshotMode.FIFO) {
            PhysicsSnapshot evicted = null;
            synchronized (fifoLock) {
                if (fifoSnapshots.size() >= MAX_FIFO_SNAPSHOTS) {
                    evicted = fifoSnapshots.pollFirst();
                    droppedSnapshots.incrementAndGet();
                }
                fifoSnapshots.offerLast(snapshot);
            }
            if (evicted != null) {
                evicted.release(snapshotPool);
            }
            return;
        }

        PhysicsSnapshot previous = latestSnapshot.getAndSet(snapshot);
        if (previous != null) {
            overwrittenSnapshots.incrementAndGet();
            previous.release(snapshotPool);
        }
    }

    private int getPendingSnapshotCount() {
        if (snapshotMode == SnapshotMode.FIFO) {
            synchronized (fifoLock) {
                return fifoSnapshots.size();
            }
        }
        return latestSnapshot.get() == null ? 0 : 1;
    }

    private PhysicsSnapshot pollFifoSnapshot() {
        synchronized (fifoLock) {
            return fifoSnapshots.pollFirst();
        }
    }

    public void stop() {
        if (isActive.compareAndSet(true, false)) {
            if (syncTask != null) syncTask.cancel();

            PhysicsSnapshot pendingLatest = latestSnapshot.getAndSet(null);
            if (pendingLatest != null) {
                pendingLatest.release(snapshotPool);
            }

            synchronized (fifoLock) {
                PhysicsSnapshot snapshot;
                while ((snapshot = fifoSnapshots.pollFirst()) != null) {
                    snapshot.release(snapshotPool);
                }
            }
        }
    }
}
