package com.ladakx.inertia.physics.factory.spawner;

import com.ladakx.inertia.physics.body.PhysicsBodyType;
import org.jetbrains.annotations.NotNull;

public interface BodySpawner {
    @NotNull
    PhysicsBodyType getType();

    boolean spawn(@NotNull BodySpawnContext context);
}