package com.ladakx.inertia.physics.body.config;

import com.ladakx.inertia.physics.body.PhysicsBodyType;

import java.util.List;
import java.util.Objects;

/**
 * Визначення для ланцюгів з додатковими параметрами з'єднань.
 */
public record ChainBodyDefinition(
        String id,
        BodyPhysicsSettings physicsSettings,
        List<String> shapeLines,
        String renderModel,
        ChainSettings chainSettings
) implements BodyDefinition {
    
    public ChainBodyDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(physicsSettings, "physicsSettings");
        Objects.requireNonNull(chainSettings, "chainSettings");
        shapeLines = List.copyOf(shapeLines);
    }

    @Override
    public PhysicsBodyType type() {
        return PhysicsBodyType.CHAIN;
    }

    /**
     * Налаштування специфічні для ланцюга.
     */
    public record ChainSettings(double jointOffset, double spacing) {}
}