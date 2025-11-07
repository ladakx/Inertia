package com.ladakx.inertia.performance.schedulers;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Abstract class for defining schedulers with common functionality.
 * This class provides a single-threaded ScheduledExecutorService for scheduling tasks.
 */
public abstract class AbstractScheduler {

    // Single-threaded executor for scheduling tasks
    public static final ScheduledExecutorService timerService = Executors.newSingleThreadScheduledExecutor();

    /**
     * Start the scheduler.
     * This method should be implemented to initialize and start the scheduling process.
     */
    abstract void start();

    /**
     * Process the scheduled tasks.
     * This method should be implemented to define the actions to be performed at each scheduled interval.
     */
    abstract void process();

    /**
     * Stop the scheduler.
     * This method should be implemented to handle the cleanup and shutdown of the scheduler.
     */
    abstract void stop();
}