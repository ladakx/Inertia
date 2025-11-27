package com.ladakx.inertia.physics.config;

import com.ladakx.inertia.jolt.object.PhysicsObjectType;

import java.util.List;
import java.util.Objects;

/**
 * Стандартне визначення для блоків та простих об'єктів.
 */
public record BlockBodyDefinition(
        String id,
        BodyPhysicsSettings physicsSettings,
        List<String> shapeLines,
        String renderModel
) implements BodyDefinition {
    public BlockBodyDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(physicsSettings, "physicsSettings");
        Objects.requireNonNull(shapeLines, "shapeLines");
        shapeLines = List.copyOf(shapeLines);
    }

    @Override
    public PhysicsObjectType type() {
        return PhysicsObjectType.BLOCK;
    }
}