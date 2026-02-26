package com.ladakx.inertia.api.transport.events;

import java.util.Objects;

public record TransportTypeRegisteredPayload(int schemaVersion,
                                            String typeId,
                                            String displayName,
                                            String ownerPlugin) implements TransportEventPayload {
    public TransportTypeRegisteredPayload {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(ownerPlugin, "ownerPlugin");
    }
}

