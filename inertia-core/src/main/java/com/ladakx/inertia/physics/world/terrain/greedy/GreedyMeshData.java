package com.ladakx.inertia.physics.world.terrain.greedy;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GreedyMeshData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<GreedyMeshShape> shapes;
    private final Map<Integer, List<GreedyMeshShape>> sectionShapes;
    private final Set<Integer> touchedSections;
    private final boolean fullRebuild;

    public GreedyMeshData(List<GreedyMeshShape> shapes) {
        this(shapes, true, null);
    }

    public GreedyMeshData(List<GreedyMeshShape> shapes, boolean fullRebuild, Set<Integer> touchedSections) {
        this.shapes = List.copyOf(shapes);
        this.fullRebuild = fullRebuild;
        this.touchedSections = touchedSections == null ? Set.of() : Set.copyOf(touchedSections);
        this.sectionShapes = buildSectionShapes(this.shapes);
    }

    private Map<Integer, List<GreedyMeshShape>> buildSectionShapes(List<GreedyMeshShape> source) {
        Map<Integer, List<GreedyMeshShape>> grouped = new HashMap<>();
        for (GreedyMeshShape shape : source) {
            int sectionY = ((int) Math.floor(shape.minY())) >> 4;
            grouped.computeIfAbsent(sectionY, unused -> new java.util.ArrayList<>()).add(shape);
        }
        grouped.replaceAll((sectionY, list) -> List.copyOf(list));
        return Map.copyOf(grouped);
    }

    public List<GreedyMeshShape> shapes() {
        return shapes;
    }

    public Map<Integer, List<GreedyMeshShape>> sectionShapes() {
        return sectionShapes;
    }

    public Set<Integer> touchedSections() {
        return touchedSections;
    }

    public boolean fullRebuild() {
        return fullRebuild;
    }

    public static GreedyMeshData empty() {
        return new GreedyMeshData(List.of());
    }
}
