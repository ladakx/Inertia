package com.ladakx.inertia.performance.pool;

import com.ladakx.inertia.InertiaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Thread class dedicated to handling physics-related tasks.
 */
public class PhysicsThread extends Thread {

    private final String space;

    // Queue for physics tasks
    private final LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();

    public PhysicsThread(String space) {
        super("RV-Physics-"+space);
        this.space = space;

        setDaemon(true); // Daemon thread does not prevent the JVM from exiting
        setUncaughtExceptionHandler((thread, error) -> {
            // Stop the thread and log the error on an uncaught exception
            isRunning.set(false);
            lastError.set(error);
            InertiaPlugin.getInstance().getLogger().log(Level.SEVERE, "Uncaught exception in PhysicsThread", error);
        });

        start();
    }

    @Override
    public void run() {
        try {
            // Main loop for processing physics tasks
            while (isRunning.get() && !isInterrupted()) {
                // Attempt to retrieve a task with a timeout to periodically check the thread state
                Runnable task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    executeTask(task);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Properly restore the interrupt status
        } finally {
            shutdown();
        }
    }

    /**
     * Executes a given task, handling any exceptions that occur.
     * @param task The task to execute.
     */
    private void executeTask(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            handleTaskException(t);
        }
    }

    /**
     * Handles exceptions thrown during task execution.
     * @param t The throwable to handle.
     */
    private void handleTaskException(Throwable t) {
        lastError.set(t);
        InertiaPlugin.getInstance().getLogger().log(Level.SEVERE, "Error executing physics task", t);
    }

    /**
     * Adds a task to the queue if the thread is still running.
     * @param task The task to execute.
     */
    public void execute(@NotNull Runnable task) {
        if (isRunning.get()) {
            taskQueue.offer(task);
        }
    }

    /**
     * Gracefully stops the thread: stops accepting new tasks and clears the queue.
     */
    public synchronized void shutdown() {
        isRunning.set(false);
        taskQueue.clear();
        interrupt();
    }

    /**
     * Returns the last recorded exception, if any.
     * @return Optional containing the last Throwable, or empty if there were no errors.
     */
    public Optional<Throwable> getLastError() {
        return Optional.ofNullable(lastError.get());
    }
}