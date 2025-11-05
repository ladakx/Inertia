package com.ladakx.inertia.core.physics.threading;

import com.github.stephengold.joltjni.TempAllocator;
// ВИДАЛЕНО: import com.github.stephengold.joltjni.TempAllocatorImpl;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.physics.jolt.JoltPhysicsSystem;

import java.util.concurrent.TimeUnit;

/**
 * Основний клас, що реалізує `Runnable` для фізичного потоку.
 * Містить головний цикл симуляції (game loop) з фіксованим кроком
 * та акумулятором часу.
 */
public final class PhysicsLoopTask implements Runnable {

    // --- Налаштування Циклу ---
    private static final double TICK_RATE_HZ = 60.0;
    private static final double FIXED_DELTA_TIME_SECONDS = 1.0 / TICK_RATE_HZ;
    private static final double MAX_FRAME_TIME_SECONDS = 0.25;
    private static final long LOOP_SLEEP_MS = 1L;

    // --- Налаштування Jolt Update ---
    // ВИДАЛЕНО: Розмір алокатора тепер в JoltPhysicsSystem
    // private static final int TEMP_ALLOCATOR_SIZE_BYTES = 10 * 1024 * 1024;
    private static final int COLLISION_STEPS = 1;

    // --- Налаштування Черги Команд ---
    private static final long COMMAND_TIME_LIMIT_NS = TimeUnit.MILLISECONDS.toNanos(2);

    // --- Стан ---
    private volatile boolean running = true;

    // --- Залежності ---
    private final InertiaPluginLogger logger;
    private final JoltPhysicsSystem joltSystems;
    private final CommandQueue commandQueue;
    private final EventGameQueue eventGameQueue;
    private final SimulationResultBuffer simulationResultBuffer;
    private final TempAllocator tempAllocator; // ВИПРАВЛЕНО: Отримуємо алокатор

    public PhysicsLoopTask(InertiaPluginLogger logger,
                           JoltPhysicsSystem joltSystems,
                           CommandQueue commandQueue,
                           EventGameQueue eventGameQueue,
                           SimulationResultBuffer simulationResultBuffer) {
        this.logger = logger;
        this.joltSystems = joltSystems;
        this.commandQueue = commandQueue;
        this.eventGameQueue = eventGameQueue;
        this.simulationResultBuffer = simulationResultBuffer;
        this.tempAllocator = joltSystems.getTempAllocator(); // ВИПРАВЛЕНО: Отримуємо алокатор тут
    }

    /**
     * Подає сигнал на зупинку циклу.
     */
    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        try {
            double accumulator = 0.0;
            long lastTimeNs = System.nanoTime();

            logger.info("Inertia-Physics-Thread started. Tick rate: " + TICK_RATE_HZ + " Hz.");

            while (running) {
                // 1. Розрахунок часу кадру
                long currentTimeNs = System.nanoTime();
                double frameTimeSeconds = (currentTimeNs - lastTimeNs) / 1_000_000_000.0;
                lastTimeNs = currentTimeNs;

                // 2. Захист від "спіралі смерті"
                if (frameTimeSeconds > MAX_FRAME_TIME_SECONDS) {
                    frameTimeSeconds = MAX_FRAME_TIME_SECONDS;
                }

                // 3. Накопичення часу
                accumulator += frameTimeSeconds;

                // 4. Виконання фіксованих кроків симуляції
                while (accumulator >= FIXED_DELTA_TIME_SECONDS && running) {
                    performSimulationStep(FIXED_DELTA_TIME_SECONDS);
                    accumulator -= FIXED_DELTA_TIME_SECONDS;
                }

                // 5. Підготовка та обмін буферами результатів
                // (Заглушка)
                // simulationResultBuffer.prepareAndSwapBuffers(...);

                // 6. Сон для зниження навантаження на CPU
                sleep();
            }

        } catch (InterruptedException e) {
            logger.info("Inertia-Physics-Thread interrupted (shutdown signal).");
        } catch (Exception e) {
            logger.severe("CRITICAL: Unhandled exception in Inertia-Physics-Thread!", e);
        } finally {
            logger.info("Inertia-Physics-Thread stopped.");
            running = false;
        }
    }

    /**
     * Виконує один повний крок фізичної симуляції.
     *
     * @param deltaTime Фіксований крок часу.
     */
    private void performSimulationStep(double deltaTime) {
        // 1. Обробка команд з Головного потоку
        commandQueue.process(COMMAND_TIME_LIMIT_NS);

        // 2. ВИПРАВЛЕНО: Більше не створюємо алокатор, а використовуємо існуючий
        try {
            joltSystems.getPhysicsSystem().update(
                    (float) deltaTime,
                    COLLISION_STEPS,
                    this.tempAllocator, // Використовуємо алокатор, що зберігається
                    joltSystems.getJobSystem()
            );

        } catch (Exception e) {
            logger.severe("Exception during Jolt physics update:", e);
        }

        // 3. Збір подій (Заглушка)
        // collectAndQueueEvents();
    }

    private void sleep() throws InterruptedException {
        Thread.sleep(LOOP_SLEEP_MS);
    }
}