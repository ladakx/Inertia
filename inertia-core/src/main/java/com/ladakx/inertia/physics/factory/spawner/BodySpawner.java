package com.ladakx.inertia.physics.factory.spawner;

import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.body.PhysicsBodyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BodySpawner {
    @NotNull
    PhysicsBodyType getType();

    @Nullable
    PhysicsBody spawnBody(@NotNull BodySpawnContext context);

    default boolean spawn(@NotNull BodySpawnContext context) {
        return spawnBody(context) != null;
    }
}
