package com.ladakx.inertia.physics.world.loop;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PhysicsLoop {

    private final String name;
    private final ScheduledExecutorService tickExecutor;
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicReference<PhysicsSnapshot> snapshotBuffer = new AtomicReference<>();
    
    private BukkitTask syncTask;
    
    // Callbacks
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

        // Producer (Physics Thread)
        tickExecutor.scheduleAtFixedRate(() -> {
            if (!isActive.get()) return;
            try {
                // 1. Simulation Step
                physicsStep.run();

                // 2. Snapshot
                if (snapshotBuffer.get() == null) {
                    PhysicsSnapshot snapshot = snapshotProducer.get();
                    if (snapshot != null) {
                        snapshotBuffer.set(snapshot);
                    }
                }
            } catch (Exception e) {
                InertiaLogger.error("Error in physics loop: " + name, e);
            }
        }, 0, periodMicros, TimeUnit.MICROSECONDS);

        // Consumer (Main Thread)
        this.syncTask = Bukkit.getScheduler().runTaskTimer(InertiaPlugin.getInstance(), () -> {
            if (!isActive.get()) return;
            PhysicsSnapshot snapshot = snapshotBuffer.getAndSet(null);
            if (snapshot != null) {
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
        }
    }
}