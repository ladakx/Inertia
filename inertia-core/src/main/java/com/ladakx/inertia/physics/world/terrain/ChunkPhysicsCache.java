package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ChunkPhysicsCache {

    private final File baseDir;
    private final Map<CacheKey, CacheEntry> memoryCache;
    private final Object lock = new Object();
    private final int maxEntries;
    private final long ttlMillis;

    public ChunkPhysicsCache(File baseDir, int maxEntries, Duration ttl) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir");
        this.maxEntries = Math.max(0, maxEntries);
        this.ttlMillis = ttl == null ? 0L : Math.max(0L, ttl.toMillis());
        this.memoryCache = new LinkedHashMap<>(16, 0.75f, true);
    }

    public Optional<GreedyMeshData> get(int x, int z) {
        CacheKey key = new CacheKey(x, z);
        CacheEntry entry;
        long now = System.currentTimeMillis();
        synchronized (lock) {
            entry = memoryCache.get(key);
            if (entry != null && isExpired(entry, now)) {
                memoryCache.remove(key);
                entry = null;
            }
            if (entry != null) {
                entry.touch(now);
            }
        }
        if (entry != null) {
            return Optional.of(entry.data());
        }

        File file = getChunkFile(x, z);
        if (!file.exists()) {
            return Optional.empty();
        }

        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(file))) {
            GreedyMeshData data = (GreedyMeshData) input.readObject();
            putInMemory(key, data);
            return Optional.of(data);
        } catch (IOException | ClassNotFoundException ex) {
            InertiaLogger.warn("Failed to read terrain cache at " + x + ", " + z, ex);
            return Optional.empty();
        }
    }

    public void put(int x, int z, GreedyMeshData data) {
        Objects.requireNonNull(data, "data");
        CacheKey key = new CacheKey(x, z);
        putInMemory(key, data);

        File file = getChunkFile(x, z);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            InertiaLogger.warn("Failed to create terrain cache directory " + parent.getAbsolutePath());
            return;
        }

        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(file))) {
            output.writeObject(data);
        } catch (IOException ex) {
            InertiaLogger.warn("Failed to write terrain cache at " + x + ", " + z, ex);
        }
    }

    public void invalidate(int x, int z) {
        CacheKey key = new CacheKey(x, z);
        synchronized (lock) {
            memoryCache.remove(key);
        }
        File file = getChunkFile(x, z);
        if (file.exists() && !file.delete()) {
            InertiaLogger.warn("Failed to delete terrain cache file " + file.getAbsolutePath());
        }
    }

    public void invalidateAll() {
        synchronized (lock) {
            memoryCache.clear();
        }
        if (!baseDir.exists()) {
            return;
        }
        File[] files = baseDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.delete()) {
                InertiaLogger.warn("Failed to delete terrain cache file " + file.getAbsolutePath());
            }
        }
    }

    private File getChunkFile(int x, int z) {
        return new File(baseDir, x + "_" + z + ".bin");
    }

    private void putInMemory(CacheKey key, GreedyMeshData data) {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            evictExpired(now);
            memoryCache.put(key, new CacheEntry(data, now));
            evictOversize();
        }
    }

    private void evictExpired(long now) {
        if (ttlMillis <= 0L || memoryCache.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<CacheKey, CacheEntry>> iterator = memoryCache.entrySet().iterator();
        while (iterator.hasNext()) {
            CacheEntry entry = iterator.next().getValue();
            if (isExpired(entry, now)) {
                iterator.remove();
            }
        }
    }

    private boolean isExpired(CacheEntry entry, long now) {
        return ttlMillis > 0L && now - entry.lastAccessMillis() > ttlMillis;
    }

    private void evictOversize() {
        if (maxEntries <= 0) {
            return;
        }
        Iterator<Map.Entry<CacheKey, CacheEntry>> iterator = memoryCache.entrySet().iterator();
        while (memoryCache.size() > maxEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private record CacheKey(int x, int z) {
    }

    private static final class CacheEntry {
        private final GreedyMeshData data;
        private long lastAccessMillis;

        private CacheEntry(GreedyMeshData data, long lastAccessMillis) {
            this.data = data;
            this.lastAccessMillis = lastAccessMillis;
        }

        public GreedyMeshData data() {
            return data;
        }

        public long lastAccessMillis() {
            return lastAccessMillis;
        }

        public void touch(long now) {
            this.lastAccessMillis = now;
        }
    }
}
