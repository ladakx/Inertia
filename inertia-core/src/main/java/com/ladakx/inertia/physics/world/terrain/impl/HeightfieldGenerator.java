package com.ladakx.inertia.physics.world.terrain.impl;

import com.ladakx.inertia.physics.world.terrain.PhysicsGenerator;
import org.bukkit.ChunkSnapshot;

public class HeightfieldGenerator implements PhysicsGenerator<Object> {
    @Override
    public Object generate(ChunkSnapshot snapshot) {
        throw new UnsupportedOperationException("HEIGHTFIELD generation is not implemented yet.");
    }
}
