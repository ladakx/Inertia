package com.ladakx.inertia.physics.world.terrain;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class GenerationQueue implements AutoCloseable {

    private final ExecutorService executor;

    public GenerationQueue(int workers) {
        int threads = Math.max(1, workers);
        this.executor = Executors.newFixedThreadPool(threads, new GenerationThreadFactory());
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
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
