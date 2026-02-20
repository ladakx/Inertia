package com.ladakx.inertia.physics.persistence.runtime;

import java.util.Objects;

public record DynamicBodyChunkKey(String world, int chunkX, int chunkZ) {
    public DynamicBodyChunkKey {
        Objects.requireNonNull(world, "world");
    }
}
