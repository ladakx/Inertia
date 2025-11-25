package com.ladakx.inertia.physics.config;

import java.util.List;
import java.util.Objects;

/**
 * Опис одного тіла з bodies.yml.
 */
public record BodyDefinition(
        String id,
        BodyPhysicsSettings physicsSettings,
        List<String> shapeLines,
        String renderModel
) {
    public BodyDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(physicsSettings, "physicsSettings");
        Objects.requireNonNull(shapeLines, "shapeLines");
        Objects.requireNonNull(renderModel, "renderModel");
        shapeLines = List.copyOf(shapeLines);
    }
}