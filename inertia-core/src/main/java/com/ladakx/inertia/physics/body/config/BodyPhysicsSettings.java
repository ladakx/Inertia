package com.ladakx.inertia.physics.body.config;

import com.github.stephengold.joltjni.enumerate.EMotionType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

/**
 * Immutable-набор физических параметров тела.
 * Добавлен параметр gravityFactor.
 */
public record BodyPhysicsSettings(
        float mass,
        float friction,
        float restitution,
        float linearDamping,
        float angularDamping,
        float gravityFactor, // Новый параметр
        EMotionType motionType,
        int objectLayer
) {

    public BodyPhysicsSettings {
        Objects.requireNonNull(motionType, "motionType");
        if (mass <= 0f) throw new IllegalArgumentException("mass must be > 0");
        friction = Math.max(0f, friction);
        restitution = Math.max(0f, restitution);
        linearDamping = Math.max(0f, linearDamping);
        angularDamping = Math.max(0f, angularDamping);
    }

    public static BodyPhysicsSettings fromConfig(ConfigurationSection section, String bodyId) {
        if (section == null) {
            return new BodyPhysicsSettings(1.0f, 0.5f, 0.0f, 0.05f, 0.05f, 1.0f, EMotionType.Dynamic, 0);
        }

        float mass = (float) section.getDouble("mass", 1.0d);
        if (mass <= 0f) mass = 0.001f;

        float friction = (float) section.getDouble("friction", 0.5d);
        float restitution = (float) section.getDouble("restitution", 0.0d);
        float linDamp = (float) section.getDouble("linear-damping", 0.05d);
        float angDamp = (float) section.getDouble("angular-damping", 0.05d);
        float gravFactor = (float) section.getDouble("gravity-factor", 1.0d);
        int layer = section.getInt("object-layer", 0);

        String motionTypeName = section.getString("motion-type", "Dynamic");
        EMotionType motionType;
        try {
            motionType = EMotionType.valueOf(motionTypeName);
        } catch (IllegalArgumentException e) {
            motionType = EMotionType.Dynamic;
        }

        return new BodyPhysicsSettings(mass, friction, restitution, linDamp, angDamp, gravFactor, motionType, layer);
    }
}