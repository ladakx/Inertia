package com.ladakx.inertia.physics.world.loop;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.snapshot.PhysicsSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.locks.LockSupport;

public class PhysicsLoop {
    private final String name;
    // Оптимизация: Используем обычный Thread вместо ScheduledExecutor для более точного контроля цикла и sleep
    private final Thread tickThread;
    private final AtomicBoolean isActive = new AtomicBoolean(false);

    // Оптимизация: ArrayBlockingQueue имеет фиксированный размер, что полезно для backpressure,
    // но ConcurrentLinkedQueue быстрее. Мы реализуем "мягкое" ограничение сами.
    private final Queue<PhysicsSnapshot> snapshotQueue = new ConcurrentLinkedQueue<>();
    private static final int MAX_QUEUE_SIZE = 3; // Максимум 3 кадра буферизации (снижает input lag)

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

    public PhysicsLoop(String name,
                       int tps,
                       int maxBodyLimit,
                       Runnable physicsStep,
                       Supplier<PhysicsSnapshot> snapshotProducer,
                       Consumer<PhysicsSnapshot> snapshotConsumer,
                       Supplier<Integer> activeBodyCounter,
                       Supplier<Integer> totalBodyCounter,
                       Supplier<Integer> staticBodyCounter) {
        this.name = name;
        if (tps <= 0) {
            InertiaLogger.warn("Invalid tick-rate (" + tps + ") for world '" + name + "'. Falling back to 20.");
            this.targetTps = 20;
        } else {
            this.targetTps = tps;
        }
        this.maxBodyLimit = maxBodyLimit;
        this.physicsStep = physicsStep;
        this.snapshotProducer = snapshotProducer;
        this.snapshotConsumer = snapshotConsumer;
        this.activeBodyCounter = activeBodyCounter;
        this.totalBodyCounter = totalBodyCounter;
        this.staticBodyCounter = staticBodyCounter;

        this.tickThread = new Thread(this::runLoop, "inertia-loop-" + name);
        this.tickThread.setDaemon(true);
        // Приоритет выше нормального, чтобы физика была плавной даже при нагрузке на CPU
        this.tickThread.setPriority(Thread.NORM_PRIORITY + 1);
        this.tickThread.setUncaughtExceptionHandler((t, e) -> {
            InertiaLogger.error("Uncaught error in physics loop thread '" + t.getName() + "'", e);
            isActive.set(false);
        });

        start();
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

                // Обрабатываем ВСЕ доступные снапшоты в этом тике, чтобы догнать физику
                // Но ограничиваемся, чтобы не повесить сервер
                int processed = 0;
                PhysicsSnapshot snapshot;
                while (processed < 5 && (snapshot = snapshotQueue.poll()) != null) {
                    snapshotConsumer.accept(snapshot);
                    processed++;
                }
            }, 1L, 1L);
        } catch (Throwable t) {
            InertiaLogger.error("Failed to start sync snapshot consumer for world '" + name + "'", t);
        }
    }

    private void runLoop() {
        long lastTime = System.nanoTime();
        long nsPerTick = 1_000_000_000 / targetTps;

        while (isActive.get()) {
            // 1. Backpressure (Тормозим физику, если сервер не успевает)
            if (snapshotQueue.size() >= MAX_QUEUE_SIZE) {
                LockSupport.parkNanos(1_000_000); // Спим 1мс
                continue;
            }

            long now = System.nanoTime();
            long deltaTime = now - lastTime;

            if (deltaTime >= nsPerTick) {
                lastTime = now;

                try {
                    long tick = tickCounter.incrementAndGet();
                    long startTick = System.nanoTime();

                    for (LoopTickListener listener : listeners) listener.onTickStart(tick);

                    // Шаг физики
                    physicsStep.run();

                    // Создание снапшота для рендера
                    PhysicsSnapshot snapshot = snapshotProducer.get();
                    if (snapshot != null) {
                        snapshotQueue.offer(snapshot);
                    }

                    long duration = System.nanoTime() - startTick;

                    // Метрики
                    if (!listeners.isEmpty()) {
                        int active = activeBodyCounter.get();
                        int total = totalBodyCounter.get();
                        int stat = staticBodyCounter.get();
                        for (LoopTickListener listener : listeners) {
                            listener.onTickEnd(tick, duration, active, total, stat, maxBodyLimit);
                        }
                    }

                } catch (Throwable e) {
                    InertiaLogger.error("Error in physics loop: " + name, e);
                    // If a fatal error happens (e.g. OOM), stop the loop to avoid spamming and undefined state.
                    isActive.set(false);
                }
            } else {
                // Умный сон до следующего тика
                long waitNs = nsPerTick - deltaTime;
                if (waitNs > 1_000_000) { // Если ждать больше 1мс
                    LockSupport.parkNanos(waitNs - 500_000); // Спим чуть меньше, чтобы не пропустить
                } else {
                    Thread.onSpinWait(); // Активное ожидание для точности
                }
            }
        }
    }

    public void stop() {
        if (isActive.compareAndSet(true, false)) {
            if (syncTask != null) syncTask.cancel();
            snapshotQueue.clear();
        }
    }
}
