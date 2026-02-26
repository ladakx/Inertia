package com.ladakx.inertia.api.transport.events;

public interface TransportEventPayload {
    int SCHEMA_VERSION_V1 = 1;

    int schemaVersion();
}

