package com.ladakx.inertia.physics.config;

import com.github.stephengold.joltjni.enumerate.EMotionType;

import java.util.Objects;

/**
 * Immutable-набір фізичних параметрів тіла.
 */
public record BodyPhysicsSettings(
        float mass,
        float friction,
        float restitution,
        float linearDamping,
        float angularDamping,
        EMotionType motionType,
        int objectLayer
) {

    public BodyPhysicsSettings {
        Objects.requireNonNull(motionType, "motionType");

        if (mass <= 0f) {
            throw new IllegalArgumentException("mass must be > 0, got " + mass);
        }
        if (friction < 0f) {
            friction = 0f;
        }
        if (restitution < 0f) {
            restitution = 0f;
        }
        if (linearDamping < 0f) {
            linearDamping = 0f;
        }
        if (angularDamping < 0f) {
            angularDamping = 0f;
        }
    }
}