package com.ladakx.inertia.api.transport.events;

import java.util.Objects;
import java.util.UUID;

public record TransportDestroyPayload(int schemaVersion,
                                     UUID transportId,
                                     String typeId,
                                     UUID worldId,
                                     String worldName,
                                     String ownerPlugin,
                                     TransportDestroyReason reason) implements TransportEventPayload {
    public TransportDestroyPayload {
        Objects.requireNonNull(transportId, "transportId");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(ownerPlugin, "ownerPlugin");
        Objects.requireNonNull(reason, "reason");
    }
}

