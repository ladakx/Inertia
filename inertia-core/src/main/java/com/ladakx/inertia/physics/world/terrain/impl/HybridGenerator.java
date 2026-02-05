package com.ladakx.inertia.physics.world.terrain.impl;

import com.ladakx.inertia.physics.world.terrain.PhysicsGenerator;
import org.bukkit.Chunk;

public class HybridGenerator implements PhysicsGenerator<Object> {
    @Override
    public Object generate(Chunk chunk) {
        throw new UnsupportedOperationException("HYBRID generation is not implemented yet.");
    }
}
