package com.ladakx.inertia.physics.world.terrain;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class GenerationQueue implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final Semaphore inFlightLimiter;
    private final int maxInFlight;

    public GenerationQueue(int workers, int maxGenerateJobsInFlight) {
        int threads = Math.max(1, workers);
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads, new GenerationThreadFactory());
        this.maxInFlight = Math.max(1, maxGenerateJobsInFlight);
        this.inFlightLimiter = new Semaphore(maxInFlight);
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return CompletableFuture.supplyAsync(() -> {
            boolean acquired = false;
            try {
                inFlightLimiter.acquire();
                acquired = true;
                return task.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                if (acquired) {
                    inFlightLimiter.release();
                }
            }
        }, executor);
    }

    public int getQueueDepth() {
        return executor.getQueue().size();
    }

    public int getInFlightJobs() {
        return maxInFlight - inFlightLimiter.availablePermits();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static class GenerationThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("inertia-terrain-worker-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
