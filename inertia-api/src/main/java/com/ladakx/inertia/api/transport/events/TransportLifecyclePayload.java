package com.ladakx.inertia.api.transport.events;

import com.ladakx.inertia.api.transport.TransportId;
import com.ladakx.inertia.api.transport.TransportType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record TransportLifecyclePayload(int schemaVersion,
                                        @NotNull TransportId transportId,
                                        @NotNull String ownerPlugin,
                                        @NotNull UUID worldId,
                                        @NotNull String worldName,
                                        @NotNull TransportType transportType) implements TransportEventPayload {
    public TransportLifecyclePayload {
        Objects.requireNonNull(transportId, "transportId");
        Objects.requireNonNull(ownerPlugin, "ownerPlugin");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(transportType, "transportType");
    }
}
