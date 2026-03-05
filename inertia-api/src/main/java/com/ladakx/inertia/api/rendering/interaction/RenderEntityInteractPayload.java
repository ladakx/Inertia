package com.ladakx.inertia.api.rendering.interaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record RenderEntityInteractPayload(int schemaVersion,
                                          @NotNull UUID playerUuid,
                                          @NotNull String playerName,
                                          @NotNull UUID worldUuid,
                                          @NotNull String worldName,
                                          int entityId,
                                          @NotNull RenderInteractionAction action,
                                          @Nullable String modelId,
                                          @Nullable String entityKey) {
    public static final int SCHEMA_VERSION_V1 = 1;
}
