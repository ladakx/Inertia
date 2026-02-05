package com.ladakx.inertia.physics.world.terrain;

import org.bukkit.ChunkSnapshot;

public interface PhysicsGenerator<T> {
    T generate(ChunkSnapshot snapshot);
}
