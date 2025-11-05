package com.ladakx.inertia.core.physics.threading;

import com.ladakx.inertia.core.InertiaPluginLogger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Потокобезпечна черга для подій, що надсилаються
 * з Фізичного потоку (Physics) до Головного потоку (Main).
 * Використовується для безпечної взаємодії з Bukkit API.
 */
public final class EventGameQueue {

    private final Queue<Runnable> queue;
    private final InertiaPluginLogger logger;

    public EventGameQueue(InertiaPluginLogger logger) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.logger = logger;
    }

    /**
     * Додає нову подію (завдання) в чергу. (Викликається з Physics потоку)
     *
     * @param task Завдання для виконання.
     */
    public void queue(Runnable task) {
        queue.add(task);
    }

    /**
     * Обробляє всі завдання в черзі. (Викликається з Main потоку)
     */
    public void processAll() {
        Runnable task;
        int processedCount = 0;

        while ((task = queue.poll()) != null) {
            try {
                task.run();
                processedCount++;
            } catch (Exception e) {
                logger.severe("Error executing game event in main thread:", e);
            }
        }
    }
}