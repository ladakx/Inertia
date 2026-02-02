package com.ladakx.inertia.physics.world.loop;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PhysicsLoop {
    private final String name;
    private final ScheduledExecutorService tickExecutor;
    private final AtomicBoolean isActive = new AtomicBoolean(false);

    private final Queue<PhysicsSnapshot> snapshotQueue = new ConcurrentLinkedQueue<>();

    private BukkitTask syncTask;
    private final Runnable physicsStep;
    private final Supplier<PhysicsSnapshot> snapshotProducer;
    private final Consumer<PhysicsSnapshot> snapshotConsumer;

    public PhysicsLoop(String name,
                       int tps,
                       Runnable physicsStep,
                       Supplier<PhysicsSnapshot> snapshotProducer,
                       Consumer<PhysicsSnapshot> snapshotConsumer) {
        this.name = name;
        this.physicsStep = physicsStep;
        this.snapshotProducer = snapshotProducer;
        this.snapshotConsumer = snapshotConsumer;
        this.tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "inertia-loop-" + name);
            t.setDaemon(true);
            return t;
        });
        start(tps);
    }

    private void start(int tps) {
        if (tps <= 0) tps = 20;
        long periodMicros = 1_000_000 / tps;
        isActive.set(true);

        tickExecutor.scheduleAtFixedRate(() -> {
            if (!isActive.get()) return;
            try {
                physicsStep.run();

                PhysicsSnapshot snapshot = snapshotProducer.get();
                if (snapshot != null) {
                    snapshotQueue.offer(snapshot);
                }
            } catch (Exception e) {
                InertiaLogger.error("Error in physics loop: " + name, e);
            }
        }, 0, periodMicros, TimeUnit.MICROSECONDS);

        this.syncTask = Bukkit.getScheduler().runTaskTimer(InertiaPlugin.getInstance(), () -> {
            if (!isActive.get()) return;

            // Drain queue: Process all accumulated frames to keep up with physics time
            // This prevents "slow motion" effect if server lags, and prevents "teleporting" if we skipped frames.
            PhysicsSnapshot snapshot;
            while ((snapshot = snapshotQueue.poll()) != null) {
                snapshotConsumer.accept(snapshot);
            }
        }, 1L, 1L);
    }

    public void stop() {
        if (isActive.compareAndSet(true, false)) {
            if (syncTask != null && !syncTask.isCancelled()) {
                syncTask.cancel();
            }
            tickExecutor.shutdown();
            try {
                if (!tickExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    tickExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                tickExecutor.shutdownNow();
            }
            snapshotQueue.clear();
        }
    }
}