package com.ladakx.inertia.physics.world.terrain;

import org.bukkit.Chunk;

public interface PhysicsGenerator<T> {
    T generate(Chunk chunk);
}
