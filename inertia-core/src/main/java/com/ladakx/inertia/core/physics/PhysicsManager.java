package com.ladakx.inertia.core.physics;

import com.ladakx.inertia.InertiaSpigotPlugin;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.nativelib.JoltNatives;
import com.ladakx.inertia.core.physics.jolt.JoltPhysicsSystem;
import com.ladakx.inertia.core.physics.threading.CommandQueue;
import com.ladakx.inertia.core.physics.threading.EventGameQueue;
import com.ladakx.inertia.core.physics.threading.PhysicsLoopTask;
import com.ladakx.inertia.core.physics.threading.SimulationResultBuffer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Центральний менеджер, що керує всім життєвим циклом фізики.
 * Ініціалізує, володіє та зупиняє Jolt, фізичний потік та канали зв'язку.
 */
public final class PhysicsManager {

    // Таймаут для очікування завершення фізичного потоку (5 секунд)
    private static final long SHUTDOWN_TIMEOUT_MS = 5000L;

    private final InertiaSpigotPlugin plugin;
    private final InertiaPluginLogger logger;
    private final EventGameQueue eventGameQueue;

    // Компоненти ядра
    private JoltPhysicsSystem joltSystems;
    private CommandQueue commandQueue;
    private SimulationResultBuffer simulationResultBuffer;
    private PhysicsLoopTask physicsLoopTask;

    private Thread physicsThread;

    /**
     * Створює новий екземпляр Менеджера Фізики.
     *
     * @param plugin         Екземпляр головного плагіна.
     * @param logger         Логгер.
     * @param eventGameQueue Черга для подій (Physics -> Main).
     */
    public PhysicsManager(InertiaSpigotPlugin plugin, InertiaPluginLogger logger, EventGameQueue eventGameQueue) {
        this.plugin = plugin;
        this.logger = logger;
        this.eventGameQueue = eventGameQueue;
    }

    /**
     * Ініціалізує Jolt-JNI, створює фізичні системи та запускає
     * виділений фізичний потік.
     *
     * @throws Throwable якщо ініціалізація Jolt або потоку провалилася.
     */
    public void initialize() throws Throwable {
        logger.info("Initializing PhysicsManager...");

        // 1. Завантаження нативної бібліотеки Jolt
        // (Може кинути UnsatisfiedLinkError, який буде перехоплено в onEnable)
        JoltNatives.loadNatives(plugin, logger);

        // 2. Ініціалізація каналів зв'язку
        this.commandQueue = new CommandQueue(logger);
        this.simulationResultBuffer = new SimulationResultBuffer();

        // 3. Ініціалізація обгорток Jolt (JobSystem, PhysicsSystem)
        logger.info("Creating Jolt Physics Systems...");
        this.joltSystems = new JoltPhysicsSystem(logger);

        // 4. Створення та запуск фізичного циклу (Physics Thread)
        logger.info("Starting Inertia-Physics-Thread...");
        this.physicsLoopTask = new PhysicsLoopTask(
                logger,
                joltSystems,
                commandQueue,
                eventGameQueue,
                simulationResultBuffer
        );

        this.physicsThread = new Thread(this.physicsLoopTask, "Inertia-Physics-Thread");
        this.physicsThread.start();

        logger.info("PhysicsManager initialized successfully.");
    }

    /**
     * Акуратно зупиняє фізичний потік та звільняє нативні ресурси.
     */
    public void shutdown() {
        logger.info("Shutting down PhysicsManager...");

        // 1. Подача сигналу на зупинку циклу
        if (this.physicsLoopTask != null) {
            this.physicsLoopTask.stop();
        }

        // 2. Переривання та очікування потоку
        if (this.physicsThread != null) {
            this.physicsThread.interrupt(); // Перериваємо, якщо він спить (sleep)
            try {
                this.physicsThread.join(SHUTDOWN_TIMEOUT_MS);
                if (this.physicsThread.isAlive()) {
                    logger.severe("Inertia-Physics-Thread did not stop within " + SHUTDOWN_TIMEOUT_MS + "ms!");
                }
            } catch (InterruptedException e) {
                logger.warning("PhysicsManager shutdown was interrupted while joining thread.");
                Thread.currentThread().interrupt();
            }
        }

        // 3. Гарантоване звільнення нативних ресурсів Jolt
        // (Використовує AutoCloseable)
        if (this.joltSystems != null) {
            try {
                this.joltSystems.close();
                logger.info("Jolt Physics Systems closed successfully.");
            } catch (Exception e) {
                logger.severe("CRITICAL: Failed to close Jolt Physics Systems!", e);
            }
        }

        this.physicsThread = null;
        this.physicsLoopTask = null;
        this.commandQueue = null;
        this.joltSystems = null;
    }

    /**
     * Потокобезпечно додає команду до черги на виконання у фізичному потоці.
     *
     * @param task Завдання (Runnable) для виконання.
     */
    public void queueCommand(Runnable task) {
        if (this.commandQueue == null) {
            logger.warning("Attempted to queue command, but CommandQueue is not initialized (plugin shutting down?)");
            return;
        }
        this.commandQueue.queue(task);
    }
}