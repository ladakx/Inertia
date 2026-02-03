package com.ladakx.inertia.physics.world.loop;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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

    // Metrics Suppliers
    private final List<LoopTickListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong tickCounter = new AtomicLong(0);
    private final Supplier<Integer> activeBodyCounter;
    private final Supplier<Integer> totalBodyCounter;
    private final Supplier<Integer> staticBodyCounter; // Пока не используется в PhysicsSystem напрямую, нужно считать отдельно
    private final int maxBodyLimit;

    public PhysicsLoop(String name,
                       int tps,
                       int maxBodyLimit,
                       Runnable physicsStep,
                       Supplier<PhysicsSnapshot> snapshotProducer,
                       Consumer<PhysicsSnapshot> snapshotConsumer,
                       Supplier<Integer> activeBodyCounter,
                       Supplier<Integer> totalBodyCounter) {
        this.name = name;
        this.maxBodyLimit = maxBodyLimit;
        this.physicsStep = physicsStep;
        this.snapshotProducer = snapshotProducer;
        this.snapshotConsumer = snapshotConsumer;
        this.activeBodyCounter = activeBodyCounter;
        this.totalBodyCounter = totalBodyCounter;
        this.staticBodyCounter = () -> 0; // TODO: Реализовать подсчет статики через ObjectManager, если нужно точно

        this.tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "inertia-loop-" + name);
            t.setDaemon(true);
            return t;
        });
        start(tps);
    }

    public void addListener(LoopTickListener listener) {
        if (listener != null) listeners.add(listener);
    }

    private void start(int tps) {
        long periodMicros = 1_000_000 / tps;
        isActive.set(true);
        tickExecutor.scheduleAtFixedRate(() -> {
            if (!isActive.get()) return;
            try {
                long tick = tickCounter.incrementAndGet();
                long start = System.nanoTime();

                for (LoopTickListener listener : listeners) listener.onTickStart(tick);

                physicsStep.run();
                PhysicsSnapshot snapshot = snapshotProducer.get();

                long duration = System.nanoTime() - start;

                // Сбор метрик
                int active = activeBodyCounter.get();
                int total = totalBodyCounter.get();
                // Для простоты считаем статику как (Total - Dynamic), но Jolt хранит их вместе.
                // В Jolt total = dynamic + static (если они добавлены в мир).
                // Мы передадим total как есть.
                // Если нужно точное число статики, его нужно пробрасывать из PhysicsWorld.
                // Пока передадим 0 или заглушку, так как Jolt PhysicsSystem.getNumBodies() возвращает всех.

                for (LoopTickListener listener : listeners) {
                    listener.onTickEnd(tick, duration, active, total, 0, maxBodyLimit);
                }

                if (snapshot != null) snapshotQueue.offer(snapshot);

            } catch (Exception e) {
                InertiaLogger.error("Error in physics loop: " + name, e);
            }
        }, 0, periodMicros, TimeUnit.MICROSECONDS);

        this.syncTask = Bukkit.getScheduler().runTaskTimer(InertiaPlugin.getInstance(), () -> {
            if (!isActive.get()) return;
            PhysicsSnapshot snapshot;
            while ((snapshot = snapshotQueue.poll()) != null) {
                snapshotConsumer.accept(snapshot);
            }
        }, 1L, 1L);
    }

    public void stop() {
        if (isActive.compareAndSet(true, false)) {
            if (syncTask != null) syncTask.cancel();
            tickExecutor.shutdownNow();
            snapshotQueue.clear();
        }
    }
}