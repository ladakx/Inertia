package com.ladakx.inertia.physics.body.config;

import com.ladakx.inertia.physics.body.PhysicsBodyType;

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
    public PhysicsBodyType type() {
        return PhysicsBodyType.BLOCK;
    }
}