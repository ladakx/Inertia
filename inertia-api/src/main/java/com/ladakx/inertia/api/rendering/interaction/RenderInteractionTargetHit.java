package com.ladakx.inertia.api.rendering.interaction;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record RenderInteractionTargetHit(int entityId,
                                         @NotNull String modelId,
                                         @NotNull String entityKey,
                                         double distance) {
    public RenderInteractionTargetHit {
        if (entityId <= 0) {
            throw new IllegalArgumentException("entityId must be positive");
        }
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(entityKey, "entityKey");
        if (distance < 0.0D) {
            throw new IllegalArgumentException("distance must be >= 0");
        }
    }
}
