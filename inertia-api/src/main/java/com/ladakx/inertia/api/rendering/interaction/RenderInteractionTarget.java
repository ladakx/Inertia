package com.ladakx.inertia.api.rendering.interaction;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record RenderInteractionTarget(int entityId,
                                      @NotNull String modelId,
                                      @NotNull String entityKey) {
    public RenderInteractionTarget {
        if (entityId <= 0) {
            throw new IllegalArgumentException("entityId must be positive");
        }
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(entityKey, "entityKey");
    }
}
