package com.ladakx.inertia.physics.debug.shapes;

import com.ladakx.inertia.physics.debug.shapes.impl.ShapeGenerators;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Реєстр доступних генераторів форм.
 */
public class ShapeManager {

    private final Map<String, ShapeGenerator> generators = new HashMap<>();

    public ShapeManager() {
        register(new ShapeGenerators.SphereGenerator());
        register(new ShapeGenerators.CubeGenerator());
        register(new ShapeGenerators.CylinderGenerator());
        register(new ShapeGenerators.PyramidGenerator());
    }

    public void register(ShapeGenerator generator) {
        generators.put(generator.getName().toLowerCase(), generator);
    }

    public ShapeGenerator getGenerator(String name) {
        return generators.get(name.toLowerCase());
    }

    public Set<String> getAvailableShapes() {
        return generators.keySet();
    }
}