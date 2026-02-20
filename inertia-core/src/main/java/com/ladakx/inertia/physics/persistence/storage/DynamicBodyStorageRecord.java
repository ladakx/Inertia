package com.ladakx.inertia.physics.persistence.storage;

import com.ladakx.inertia.api.body.MotionType;

import java.util.Objects;
import java.util.UUID;

public record DynamicBodyStorageRecord(
        UUID objectId,
        String world,
        String bodyId,
        double x,
        double y,
        double z,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        double linearVelocityX,
        double linearVelocityY,
        double linearVelocityZ,
        double angularVelocityX,
        double angularVelocityY,
        double angularVelocityZ,
        float friction,
        float restitution,
        float gravityFactor,
        MotionType motionType,
        int chunkX,
        int chunkZ,
        long savedAtEpochMillis
) {
    public DynamicBodyStorageRecord {
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(motionType, "motionType");
    }
}
