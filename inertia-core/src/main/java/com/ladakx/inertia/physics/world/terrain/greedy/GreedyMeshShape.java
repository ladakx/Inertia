package com.ladakx.inertia.physics.world.terrain.greedy;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record GreedyMeshShape(
        String materialId,
        float density,
        float friction,
        float restitution,
        List<SerializedBoundingBox> boundingBoxes,
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
