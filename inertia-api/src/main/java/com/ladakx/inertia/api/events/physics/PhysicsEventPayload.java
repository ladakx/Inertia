package com.ladakx.inertia.api.events.physics;

public interface PhysicsEventPayload {
    int SCHEMA_VERSION_V1 = 1;

    int schemaVersion();
}
