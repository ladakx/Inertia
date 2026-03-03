package com.ladakx.inertia.core.impl.rendering.transform;

import com.ladakx.inertia.api.rendering.transform.EntityTransformAlgorithm;
import com.ladakx.inertia.api.rendering.transform.RenderTransformService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class RenderTransformServiceImpl implements RenderTransformService {

    public static final String DEFAULT_ALGORITHM_ID = "inertia:default";

    private record AlgorithmEntry(Plugin owner, EntityTransformAlgorithm algorithm) {}
    private record EntityBinding(String modelId, String entityKey) {}

    private final Map<String, AlgorithmEntry> algorithms = new ConcurrentHashMap<>();
    private final Map<EntityBinding, String> bindings = new ConcurrentHashMap<>();
    private volatile String defaultAlgorithmId = DEFAULT_ALGORITHM_ID;

    public RenderTransformServiceImpl(@NotNull Plugin inertiaPlugin) {
        Objects.requireNonNull(inertiaPlugin, "inertiaPlugin");
        algorithms.put(DEFAULT_ALGORITHM_ID, new AlgorithmEntry(inertiaPlugin, new DefaultEntityTransformAlgorithm()));
    }

    @Override
    public @NotNull String defaultAlgorithmId() {
        return defaultAlgorithmId;
    }

    @Override
    public void setDefaultAlgorithm(@NotNull String algorithmId) {
        String normalizedId = normalizeId(algorithmId);
        if (!algorithms.containsKey(normalizedId)) {
            throw new IllegalArgumentException("Unknown transform algorithm id: " + normalizedId);
        }
        this.defaultAlgorithmId = normalizedId;
    }

    @Override
    public void registerAlgorithm(@NotNull Plugin owner, @NotNull String algorithmId, @NotNull EntityTransformAlgorithm algorithm) {
        Objects.requireNonNull(owner, "owner");
        String normalizedId = normalizeId(algorithmId);
        Objects.requireNonNull(algorithm, "algorithm");
        AlgorithmEntry prev = algorithms.putIfAbsent(normalizedId, new AlgorithmEntry(owner, algorithm));
        if (prev != null) {
            throw new IllegalStateException("Transform algorithm already registered: " + normalizedId);
        }
    }

    @Override
    public boolean unregisterAlgorithm(@NotNull String algorithmId) {
        String normalizedId = normalizeId(algorithmId);
        if (DEFAULT_ALGORITHM_ID.equals(normalizedId)) {
            return false;
        }
        boolean removed = algorithms.remove(normalizedId) != null;
        if (removed) {
            if (normalizedId.equals(defaultAlgorithmId)) {
                defaultAlgorithmId = DEFAULT_ALGORITHM_ID;
            }
            bindings.entrySet().removeIf(e -> normalizedId.equals(e.getValue()));
        }
        return removed;
    }

    @Override
    public @Nullable EntityTransformAlgorithm getAlgorithm(@NotNull String algorithmId) {
        String normalizedId = normalizeId(algorithmId);
        AlgorithmEntry entry = algorithms.get(normalizedId);
        return entry != null ? entry.algorithm : null;
    }

    @Override
    public void bindEntityAlgorithm(@NotNull String modelId, @NotNull String entityKey, @NotNull String algorithmId) {
        String normalizedModelId = normalizeKey(modelId, "modelId");
        String normalizedEntityKey = normalizeKey(entityKey, "entityKey");
        String normalizedAlgorithmId = normalizeId(algorithmId);
        if (!algorithms.containsKey(normalizedAlgorithmId)) {
            throw new IllegalArgumentException("Unknown transform algorithm id: " + normalizedAlgorithmId);
        }
        bindings.put(new EntityBinding(normalizedModelId, normalizedEntityKey), normalizedAlgorithmId);
    }

    @Override
    public boolean unbindEntityAlgorithm(@NotNull String modelId, @NotNull String entityKey) {
        String normalizedModelId = normalizeKey(modelId, "modelId");
        String normalizedEntityKey = normalizeKey(entityKey, "entityKey");
        return bindings.remove(new EntityBinding(normalizedModelId, normalizedEntityKey)) != null;
    }

    @Override
    public @Nullable String getBoundAlgorithmId(@NotNull String modelId, @NotNull String entityKey) {
        String normalizedModelId = normalizeKey(modelId, "modelId");
        String normalizedEntityKey = normalizeKey(entityKey, "entityKey");
        return bindings.get(new EntityBinding(normalizedModelId, normalizedEntityKey));
    }

    @Override
    public @NotNull EntityTransformAlgorithm resolveAlgorithm(@NotNull String modelId, @NotNull String entityKey) {
        String normalizedModelId = normalizeKey(modelId, "modelId");
        String normalizedEntityKey = normalizeKey(entityKey, "entityKey");
        String boundId = bindings.get(new EntityBinding(normalizedModelId, normalizedEntityKey));
        if (boundId != null) {
            AlgorithmEntry bound = algorithms.get(boundId);
            if (bound != null) {
                return bound.algorithm;
            }
            bindings.remove(new EntityBinding(normalizedModelId, normalizedEntityKey), boundId);
        }
        AlgorithmEntry fallback = algorithms.get(defaultAlgorithmId);
        if (fallback == null) {
            defaultAlgorithmId = DEFAULT_ALGORITHM_ID;
            fallback = algorithms.get(DEFAULT_ALGORITHM_ID);
        }
        if (fallback == null) {
            throw new IllegalStateException("Default transform algorithm is not registered.");
        }
        return fallback.algorithm;
    }

    @Override
    public void unregisterAll(@NotNull Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        algorithms.entrySet().removeIf(entry ->
                !DEFAULT_ALGORITHM_ID.equals(entry.getKey()) && entry.getValue().owner.equals(owner));
        bindings.entrySet().removeIf(entry -> !algorithms.containsKey(entry.getValue()));
    }

    private static String normalizeId(String id) {
        return normalizeKey(id, "algorithmId");
    }

    private static String normalizeKey(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
