package com.ladakx.inertia.physics.world.terrain.greedy;

import java.io.Serial;
import java.io.Serializable;

public record SerializedBoundingBox(
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
