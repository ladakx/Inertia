package com.ladakx.inertia.core.physics.threading;

import com.ladakx.inertia.core.InertiaPluginLogger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Потокобезпечна черга для команд, що надсилаються
 * з Головного потоку (Main) до Фізичного потоку (Physics).
 * Реалізує ліміт часу обробки для запобігання "backpressure".
 */
public final class CommandQueue {

    private final Queue<Runnable> queue;
    private final InertiaPluginLogger logger;

    public CommandQueue(InertiaPluginLogger logger) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.logger = logger;
    }

    /**
     * Додає нову команду в чергу. (Викликається з Main потоку)
     *
     * @param task Завдання для виконання.
     */
    public void queue(Runnable task) {
        queue.add(task);
    }

    /**
     * Обробляє команди з черги протягом обмеженого часу.
     * (Викликається з Physics потоку)
     *
     * @param timeLimitNs Максимальний час в наносекундах,
     * який можна витратити на обробку команд.
     * @return Кількість оброблених команд.
     */
    public int process(long timeLimitNs) {
        long startTimeNs = System.nanoTime();
        int processedCount = 0;

        while (System.nanoTime() - startTimeNs < timeLimitNs) {
            Runnable task = queue.poll();
            if (task == null) {
                // Черга порожня
                break;
            }

            try {
                task.run();
                processedCount++;
            } catch (Exception e) {
                logger.severe("Error executing command in physics thread:", e);
            }
        }

        int remaining = queue.size();
        if (remaining > 0) {
            logger.warning("CommandQueue processing time limit exceeded. "
                    + processedCount + " tasks processed, "
                    + remaining + " tasks remaining.");
        }

        return processedCount;
    }
}