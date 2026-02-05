package com.ladakx.inertia.physics.world.terrain.greedy;

import java.io.Serial;
import java.io.Serializable;

public record GreedyMeshShape(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
