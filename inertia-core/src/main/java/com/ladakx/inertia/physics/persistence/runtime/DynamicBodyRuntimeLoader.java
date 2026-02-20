package com.ladakx.inertia.physics.persistence.runtime;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.persistence.storage.DynamicBodyStorageFile;
import com.ladakx.inertia.physics.persistence.storage.DynamicBodyStorageRecord;
import com.ladakx.inertia.physics.persistence.validation.DynamicBodyValidator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DynamicBodyRuntimeLoader {

    private static final int DEFAULT_BATCH_SIZE = 256;

    private final InertiaPlugin plugin;
    private final BodyFactory bodyFactory;
    private final DynamicBodyValidator validator;
    private final DynamicBodyStorageFile storageFile;
    private final Executor ioExecutor;
    private final int batchSize;

    private final Map<DynamicBodyChunkKey, ConcurrentLinkedQueue<DynamicBodyStorageRecord>> pendingByChunk = new ConcurrentHashMap<>();
    private final Set<java.util.UUID> loadedObjectIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private volatile BukkitTask flushTask;

    public DynamicBodyRuntimeLoader(InertiaPlugin plugin,
                                    BodyFactory bodyFactory,
                                    DynamicBodyValidator validator,
                                    Path storagePath,
                                    int batchSize) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.bodyFactory = Objects.requireNonNull(bodyFactory, "bodyFactory");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.storageFile = new DynamicBodyStorageFile(Objects.requireNonNull(storagePath, "storagePath"));
        this.batchSize = Math.max(1, batchSize);
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "inertia-dynamic-body-loader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public DynamicBodyRuntimeLoader(InertiaPlugin plugin,
                                    BodyFactory bodyFactory,
                                    DynamicBodyValidator validator,
                                    Path storagePath) {
        this(plugin, bodyFactory, validator, storagePath, DEFAULT_BATCH_SIZE);
    }

    public CompletableFuture<Void> loadAsync() {
        if (!loading.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(storageFile::read, ioExecutor)
                .thenAccept(records -> {
                    List<DynamicBodyStorageRecord> valid = new ArrayList<>(records.size());
                    for (DynamicBodyStorageRecord record : records) {
                        if (validator.isValid(record)) {
                            valid.add(record);
                        }
                    }
                    for (DynamicBodyStorageRecord record : valid) {
                        DynamicBodyChunkKey key = new DynamicBodyChunkKey(record.world(), record.chunkX(), record.chunkZ());
                        pendingByChunk.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>()).offer(record);
                    }
                })
                .whenComplete((unused, throwable) -> {
                    loading.set(false);
                    if (throwable != null) {
                        InertiaLogger.error("Dynamic body async load failed", throwable);
                    }
                    scheduleFlushTask();
                    flushLoadedChunks();
                });
    }

    public void onChunkLoaded(String world, int chunkX, int chunkZ) {
        Objects.requireNonNull(world, "world");
        DynamicBodyChunkKey key = new DynamicBodyChunkKey(world, chunkX, chunkZ);
        ConcurrentLinkedQueue<DynamicBodyStorageRecord> queue = pendingByChunk.get(key);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        scheduleFlushTask();
    }

    public void clear() {
        pendingByChunk.clear();
        loadedObjectIds.clear();
        BukkitTask task = flushTask;
        if (task != null) {
            task.cancel();
        }
        flushTask = null;
    }

    private void flushLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            org.bukkit.Chunk[] chunks = world.getLoadedChunks();
            for (org.bukkit.Chunk chunk : chunks) {
                onChunkLoaded(world.getName(), chunk.getX(), chunk.getZ());
            }
        }
    }

    private void scheduleFlushTask() {
        if (flushTask != null) {
            return;
        }
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushBatchOnMainThread, 1L, 1L);
    }

    private void flushBatchOnMainThread() {
        int remaining = batchSize;
        boolean hasPending = false;
        for (Map.Entry<DynamicBodyChunkKey, ConcurrentLinkedQueue<DynamicBodyStorageRecord>> entry : pendingByChunk.entrySet()) {
            DynamicBodyChunkKey key = entry.getKey();
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                if (!entry.getValue().isEmpty()) {
                    hasPending = true;
                }
                continue;
            }

            ConcurrentLinkedQueue<DynamicBodyStorageRecord> queue = entry.getValue();
            DynamicBodyStorageRecord record;
            while (remaining > 0 && (record = queue.poll()) != null) {
                if (loadedObjectIds.add(record.objectId())) {
                    Location location = new Location(world, record.x(), record.y(), record.z());
                    bodyFactory.spawnBody(location, record.bodyId());
                }
                remaining--;
            }

            if (queue.isEmpty()) {
                pendingByChunk.remove(key, queue);
            } else {
                hasPending = true;
            }

            if (remaining == 0) {
                break;
            }
        }

        if (!hasPending && pendingByChunk.isEmpty()) {
            BukkitTask task = flushTask;
            if (task != null) {
                task.cancel();
            }
            flushTask = null;
        }
    }
}
