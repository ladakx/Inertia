package com.ladakx.inertia.physics.debug.shapes;

import com.ladakx.inertia.physics.debug.shapes.impl.DebugShapeGenerators;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Реєстр доступних генераторів форм.
 */
public class DebugShapeManager {

    private final Map<String, DebugShapeGenerator> generators = new HashMap<>();

    public DebugShapeManager() {
        register(new DebugShapeGenerators.SphereGeneratorDebug());
        register(new DebugShapeGenerators.CubeGeneratorDebug());
        register(new DebugShapeGenerators.CylinderGeneratorDebug());
        register(new DebugShapeGenerators.PyramidGeneratorDebug());
    }

    public void register(DebugShapeGenerator generator) {
        generators.put(generator.getName().toLowerCase(), generator);
    }

    public DebugShapeGenerator getGenerator(String name) {
        return generators.get(name.toLowerCase());
    }

    public Set<String> getAvailableShapes() {
        return generators.keySet();
    }
}