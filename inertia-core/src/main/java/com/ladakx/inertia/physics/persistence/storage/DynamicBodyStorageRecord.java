package com.ladakx.inertia.physics.persistence.storage;

import com.ladakx.inertia.api.body.MotionType;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DynamicBodyStorageRecord(
        UUID clusterId,
        String world,
        String bodyId,
        MotionType motionType,
        float friction,
        float restitution,
        float gravityFactor,
        int chunkX,
        int chunkZ,
        long savedAtEpochMillis,
        Map<String, String> customData,
        List<PartState> parts
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    public DynamicBodyStorageRecord {
        Objects.requireNonNull(clusterId, "clusterId");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(motionType, "motionType");
        Objects.requireNonNull(customData, "customData");
        Objects.requireNonNull(parts, "parts");
    }
}