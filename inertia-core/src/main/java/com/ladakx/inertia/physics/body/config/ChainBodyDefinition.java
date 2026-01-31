package com.ladakx.inertia.physics.body.config;

import com.ladakx.inertia.physics.body.PhysicsBodyType;

import java.util.List;
import java.util.Objects;

public record ChainBodyDefinition(
        String id,
        BodyPhysicsSettings physicsSettings,
        List<String> shapeLines,
        String renderModel,
        ChainCreationSettings creation,
        ChainStabilizationSettings stabilization,
        ChainLimitSettings limits,
        AdaptiveSettings adaptive // Новое поле
) implements BodyDefinition {

    public ChainBodyDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(physicsSettings, "physicsSettings");
        Objects.requireNonNull(creation, "creation");
        Objects.requireNonNull(stabilization, "stabilization");
        Objects.requireNonNull(limits, "limits");
        // adaptive может быть null, если не используется (тогда используем дефолт)
        if (adaptive == null) adaptive = new AdaptiveSettings(false, 0, 0, 0f, 0f, 0, 0);
        shapeLines = List.copyOf(shapeLines);
    }

    @Override
    public PhysicsBodyType type() {
        return PhysicsBodyType.CHAIN;
    }

    public record ChainCreationSettings(double jointOffset, double spacing) {}

    public record ChainStabilizationSettings(int positionIterations, int velocityIterations) {}

    public record ChainLimitSettings(float swingLimitAngle, TwistMode twistMode) {}

    /**
     * Настройки адаптивной физики.
     * Значения интерполируются линейно между minLength и maxLength.
     */
    public record AdaptiveSettings(
            boolean enabled,
            int minLength,
            int maxLength,
            float maxGravity, // Гравитация для коротких цепей (обычно 1.0)
            float minGravity, // Гравитация для длинных цепей (обычно 0.1)
            int minIterations, // Итерации для коротких
            int maxIterations  // Итерации для длинных
    ) {}

    public enum TwistMode {
        FREE, LOCKED, LIMITED
    }
}