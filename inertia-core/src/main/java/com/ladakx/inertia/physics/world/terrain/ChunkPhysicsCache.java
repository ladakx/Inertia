package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.common.logging.InertiaLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ChunkPhysicsCache {

    private static final int CACHE_FORMAT_VERSION = 3;

    private final File baseDir;
    private final Map<CacheKey, CacheEntry> memoryCache;
    private final Object lock = new Object();
    private final int maxEntries;
    private final long memoryTtlMillis;
    private final long diskTtlMillis;
    private final String pluginVersion;
    private final long worldSeed;
    private final String configHash;
    private final CacheGenerationMetadata expectedMetadata;

    public ChunkPhysicsCache(File baseDir,
                             int maxEntries,
                             Duration memoryTtl,
                             Duration diskTtl,
                             String pluginVersion,
                             long worldSeed,
                             String configHash,
                             CacheGenerationMetadata expectedMetadata) {
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        Objects.requireNonNull(configHash, "configHash");
        this.expectedMetadata = Objects.requireNonNull(expectedMetadata, "expectedMetadata");
        this.baseDir = new File(Objects.requireNonNull(baseDir, "baseDir"),
                "v" + CACHE_FORMAT_VERSION + "_" + sanitize(pluginVersion) + "_" + worldSeed + "_" + sanitize(configHash));
        this.maxEntries = Math.max(0, maxEntries);
        this.memoryTtlMillis = memoryTtl == null ? 0L : Math.max(0L, memoryTtl.toMillis());
        this.diskTtlMillis = diskTtl == null ? 0L : Math.max(0L, diskTtl.toMillis());
        this.pluginVersion = pluginVersion;
        this.worldSeed = worldSeed;
        this.configHash = configHash;
        this.memoryCache = new LinkedHashMap<>(16, 0.75f, true);
    }

    public Optional<CachedChunkPhysicsData> get(int x, int z) {
        CacheKey key = new CacheKey(x, z);
        CacheEntry entry;
        long now = System.currentTimeMillis();
        synchronized (lock) {
            entry = memoryCache.get(key);
            if (entry != null && isExpired(entry, now, memoryTtlMillis)) {
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
        if (isDiskEntryExpired(file, now)) {
            if (!file.delete()) {
                InertiaLogger.warn("Failed to delete expired terrain cache file " + file.getAbsolutePath());
            }
            return Optional.empty();
        }

        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(file))) {
            CachePayload payload = (CachePayload) input.readObject();
            if (!isCompatible(payload)) {
                if (!file.delete()) {
                    InertiaLogger.warn("Failed to delete incompatible terrain cache file " + file.getAbsolutePath());
                }
                return Optional.empty();
            }
            CachedChunkPhysicsData data = payload.data();
            putInMemory(key, data);
            return Optional.of(data);
        } catch (IOException | ClassNotFoundException | ClassCastException ex) {
            InertiaLogger.warn("Failed to read terrain cache at " + x + ", " + z, ex);
            return Optional.empty();
        }
    }

    public void put(int x, int z, CachedChunkPhysicsData data) {
        Objects.requireNonNull(data, "data");
        CacheKey key = new CacheKey(x, z);
        putInMemory(key, data);

        File file = getChunkFile(x, z);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            InertiaLogger.warn("Failed to create terrain cache directory " + parent.getAbsolutePath());
            return;
        }

        CachePayload payload = new CachePayload(
                CACHE_FORMAT_VERSION,
                pluginVersion,
                worldSeed,
                configHash,
                expectedMetadata,
                data
        );

        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(file))) {
            output.writeObject(payload);
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

    private boolean isCompatible(CachePayload payload) {
        return payload != null
                && payload.formatVersion() == CACHE_FORMAT_VERSION
                && pluginVersion.equals(payload.pluginVersion())
                && worldSeed == payload.worldSeed()
                && configHash.equals(payload.configHash())
                && expectedMetadata.equals(payload.metadata())
                && payload.data() != null;
    }

    private boolean isDiskEntryExpired(File file, long now) {
        return diskTtlMillis > 0L && now - file.lastModified() > diskTtlMillis;
    }

    private File getChunkFile(int x, int z) {
        return new File(baseDir, x + "_" + z + ".bin");
    }

    private void putInMemory(CacheKey key, CachedChunkPhysicsData data) {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            evictExpired(now);
            memoryCache.put(key, new CacheEntry(data, now));
            evictOversize();
        }
    }

    private void evictExpired(long now) {
        if (memoryTtlMillis <= 0L || memoryCache.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<CacheKey, CacheEntry>> iterator = memoryCache.entrySet().iterator();
        while (iterator.hasNext()) {
            CacheEntry entry = iterator.next().getValue();
            if (isExpired(entry, now, memoryTtlMillis)) {
                iterator.remove();
            }
        }
    }

    private boolean isExpired(CacheEntry entry, long now, long ttlMillis) {
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

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record CacheKey(int x, int z) {
    }

    private static final class CacheEntry {
        private final CachedChunkPhysicsData data;
        private long lastAccessMillis;

        private CacheEntry(CachedChunkPhysicsData data, long lastAccessMillis) {
            this.data = data;
            this.lastAccessMillis = lastAccessMillis;
        }

        public CachedChunkPhysicsData data() {
            return data;
        }

        public long lastAccessMillis() {
            return lastAccessMillis;
        }

        public void touch(long now) {
            this.lastAccessMillis = now;
        }
    }

    private record CachePayload(int formatVersion,
                                String pluginVersion,
                                long worldSeed,
                                String configHash,
                                CacheGenerationMetadata metadata,
                                CachedChunkPhysicsData data) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record CacheGenerationMetadata(String generationMethod,
                                          int algorithmVersion,
                                          Map<String, String> parameters) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public CacheGenerationMetadata {
            generationMethod = Objects.requireNonNullElse(generationMethod, "UNKNOWN");
            if (algorithmVersion < 0) {
                throw new IllegalArgumentException("algorithmVersion must be >= 0");
            }
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }

        @Override
        public Map<String, String> parameters() {
            return Map.copyOf(parameters);
        }

        public static CacheGenerationMetadata of(String generationMethod,
                                                 int algorithmVersion,
                                                 Map<String, String> parameters) {
            return new CacheGenerationMetadata(generationMethod, algorithmVersion, normalizeParameters(parameters));
        }

        private static Map<String, String> normalizeParameters(Map<String, String> parameters) {
            if (parameters == null || parameters.isEmpty()) {
                return Map.of();
            }
            Set<String> keys = new LinkedHashSet<>(parameters.keySet());
            Map<String, String> normalized = new LinkedHashMap<>();
            keys.stream().sorted().forEach(key -> normalized.put(
                    Objects.requireNonNullElse(key, "<null>"),
                    Objects.requireNonNullElse(parameters.get(key), "<null>")
            ));
            return normalized;
        }
    }
}
