package com.ladakx.inertia.physics.config;

import com.ladakx.inertia.jolt.object.PhysicsObjectType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Складне визначення для Ragdoll (лялька з фізикою).
 */
public record RagdollDefinition(
        String id,
        Map<String, String> renderModels,      // Map<LimbName, ModelId>
        RagdollJointSettings jointSettings,    // Global joint settings
        RagdollPhysicsSettings physicsSettings // Mass + Shapes map
) implements BodyDefinition {

    public RagdollDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(renderModels, "renderModels");
        Objects.requireNonNull(jointSettings, "jointSettings");
        Objects.requireNonNull(physicsSettings, "physicsSettings");
        renderModels = Map.copyOf(renderModels);
    }

    @Override
    public PhysicsObjectType type() {
        return PhysicsObjectType.RAGDOLL;
    }

    public record RagdollJointSettings(
            double armOffsetDivisor,
            double legOffsetX,
            List<String> fixedAxes
    ) {
        public RagdollJointSettings {
            fixedAxes = List.copyOf(fixedAxes);
        }
    }

    public record RagdollPhysicsSettings(
            float mass,
            BodyPhysicsSettings baseSettings, // friction, damping, etc.
            Map<String, List<String>> shapes  // Map<PartName, List<ShapeString>>
    ) {
        public RagdollPhysicsSettings {
            shapes = Map.copyOf(shapes);
        }
    }
}