package com.ladakx.inertia.physics.world.terrain.greedy;

import java.io.Serial;
import java.io.Serializable;

public record GreedyMeshShape(
        String materialId,
        float density,
        float friction,
        float restitution,
        // Сырой массив координат треугольников. Каждые 9 флоатов = 1 треугольник (3 вершины * 3 координаты)
        float[] vertices,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;
}
