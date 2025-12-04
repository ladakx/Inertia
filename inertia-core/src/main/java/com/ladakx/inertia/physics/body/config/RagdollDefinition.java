package com.ladakx.inertia.physics.body.config;

import com.github.stephengold.joltjni.enumerate.EAxis;
import com.ladakx.inertia.jolt.object.PhysicsObjectType;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record RagdollDefinition(
        String id,
        Map<String, RagdollPartDefinition> parts
) implements BodyDefinition {

    public RagdollDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parts, "parts");
        parts = Map.copyOf(parts);
    }

    @Override
    public PhysicsObjectType type() {
        return PhysicsObjectType.RAGDOLL;
    }

    /**
     * Опис однієї частини тіла.
     */
    public record RagdollPartDefinition(
            String renderModelId,
            float mass,
            Vector size,
            String shapeString,
            String parentName,
            boolean collideWithParent,
            JointSettings joint,
            PartPhysicsSettings physics // Нове поле для фізики (тертя, damping)
    ) {}

    /**
     * Фізичні параметри частини тіла.
     */
    public record PartPhysicsSettings(
            float linearDamping,
            float angularDamping,
            float friction,
            float restitution
    ) {}

    /**
     * Налаштування з'єднання.
     */
    public record JointSettings(
            Vector pivotOnParent,
            Vector pivotOnChild,
            java.util.List<String> fixedAxes,
            Map<EAxis, RotationLimit> limits // Нове поле: ліміти обертання
    ) {
        public JointSettings {
            if (fixedAxes == null) fixedAxes = Collections.emptyList();
            if (limits == null) limits = Collections.emptyMap();
        }
    }

    /**
     * Ліміт обертання (в радіанах).
     */
    public record RotationLimit(float min, float max) {}
}