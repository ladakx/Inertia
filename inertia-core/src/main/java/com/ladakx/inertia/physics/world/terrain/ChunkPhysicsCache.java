package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkPhysicsCache {

    private final File baseDir;
    private final Map<CacheKey, GreedyMeshData> memoryCache = new ConcurrentHashMap<>();

    public ChunkPhysicsCache(File baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir");
    }

    public Optional<GreedyMeshData> get(int x, int z) {
        CacheKey key = new CacheKey(x, z);
        GreedyMeshData cached = memoryCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        File file = getChunkFile(x, z);
        if (!file.exists()) {
            return Optional.empty();
        }

        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(file))) {
            GreedyMeshData data = (GreedyMeshData) input.readObject();
            memoryCache.put(key, data);
            return Optional.of(data);
        } catch (IOException | ClassNotFoundException ex) {
            InertiaLogger.warn("Failed to read terrain cache at " + x + ", " + z, ex);
            return Optional.empty();
        }
    }

    public void put(int x, int z, GreedyMeshData data) {
        Objects.requireNonNull(data, "data");
        CacheKey key = new CacheKey(x, z);
        memoryCache.put(key, data);

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
        memoryCache.remove(key);
        File file = getChunkFile(x, z);
        if (file.exists() && !file.delete()) {
            InertiaLogger.warn("Failed to delete terrain cache file " + file.getAbsolutePath());
        }
    }

    public void invalidateAll() {
        memoryCache.clear();
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

    private record CacheKey(int x, int z) {
    }
}
