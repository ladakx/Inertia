package com.ladakx.inertia.physics.body;

import com.ladakx.inertia.api.body.MotionType;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public interface InertiaPhysicsBody {

    @NotNull
    String getBodyId();

    @NotNull
    PhysicsBodyType getType();

    boolean isValid();

    void destroy();

    // --- Позиционирование ---

    @NotNull
    Location getLocation();

    void teleport(@NotNull Location location);

    void move(@NotNull Vector offset);

    // --- Движение ---

    @NotNull
    Vector getLinearVelocity();

    void setLinearVelocity(@NotNull Vector velocity);

    @NotNull
    Vector getAngularVelocity();

    void setAngularVelocity(@NotNull Vector velocity);

    void addImpulse(@NotNull Vector impulse);

    void addTorque(@NotNull Vector torque);

    // --- Свойства ---

    void setFriction(float friction);

    float getFriction();

    void setRestitution(float restitution);

    float getRestitution();

    void setGravityFactor(float factor);

    float getGravityFactor();

    // --- Состояние ---

    void activate();

    void deactivate();

    boolean isActive();

    void setMotionType(@NotNull MotionType motionType);

    @NotNull
    MotionType getMotionType();
}