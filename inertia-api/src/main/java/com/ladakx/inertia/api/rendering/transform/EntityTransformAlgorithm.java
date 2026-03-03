package com.ladakx.inertia.api.rendering.transform;

import org.jetbrains.annotations.NotNull;

/**
 * Pluggable world transform computation for a single render entity/model part.
 */
@FunctionalInterface
public interface EntityTransformAlgorithm {

    void compute(@NotNull EntityTransformContext context, @NotNull MutableEntityTransform output);
}

