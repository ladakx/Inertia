package com.ladakx.inertia.api.rendering.transform;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registry and resolver for pluggable entity transform algorithms.
 */
public interface RenderTransformService {

    @NotNull String defaultAlgorithmId();

    void setDefaultAlgorithm(@NotNull String algorithmId);

    void registerAlgorithm(@NotNull Plugin owner, @NotNull String algorithmId, @NotNull EntityTransformAlgorithm algorithm);

    boolean unregisterAlgorithm(@NotNull String algorithmId);

    @Nullable EntityTransformAlgorithm getAlgorithm(@NotNull String algorithmId);

    void bindEntityAlgorithm(@NotNull String modelId, @NotNull String entityKey, @NotNull String algorithmId);

    boolean unbindEntityAlgorithm(@NotNull String modelId, @NotNull String entityKey);

    @Nullable String getBoundAlgorithmId(@NotNull String modelId, @NotNull String entityKey);

    @NotNull EntityTransformAlgorithm resolveAlgorithm(@NotNull String modelId, @NotNull String entityKey);

    void unregisterAll(@NotNull Plugin owner);
}
