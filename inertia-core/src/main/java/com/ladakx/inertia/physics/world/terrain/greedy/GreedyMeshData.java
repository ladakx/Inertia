package com.ladakx.inertia.physics.world.terrain.greedy;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class GreedyMeshData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<GreedyMeshShape> shapes;

    public GreedyMeshData(List<GreedyMeshShape> shapes) {
        this.shapes = List.copyOf(shapes);
    }

    public List<GreedyMeshShape> shapes() {
        return shapes;
    }

    public static GreedyMeshData empty() {
        return new GreedyMeshData(List.of());
    }
}
