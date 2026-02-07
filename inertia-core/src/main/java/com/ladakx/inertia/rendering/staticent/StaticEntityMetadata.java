package com.ladakx.inertia.rendering.staticent;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record StaticEntityMetadata(
        String bodyId,
        UUID bodyUuid,
        String renderModelId,
        String renderEntityKey,
        @Nullable UUID clusterId
) {
    public StaticEntityMetadata {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(renderModelId, "renderModelId");
        Objects.requireNonNull(renderEntityKey, "renderEntityKey");
    }
}

