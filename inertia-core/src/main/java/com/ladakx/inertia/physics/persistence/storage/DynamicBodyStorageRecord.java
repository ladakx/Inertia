package com.ladakx.inertia.physics.persistence.storage;

import java.util.Objects;
import java.util.UUID;

public record DynamicBodyStorageRecord(
        UUID objectId,
        String world,
        String bodyId,
        double x,
        double y,
        double z,
        int chunkX,
        int chunkZ,
        long savedAtEpochMillis
) {
    public DynamicBodyStorageRecord {
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(bodyId, "bodyId");
    }
}
