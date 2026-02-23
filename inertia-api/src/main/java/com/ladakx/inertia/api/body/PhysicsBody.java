package com.ladakx.inertia.api.body;

import com.ladakx.inertia.physics.body.PhysicsBodyType;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public interface PhysicsBody {

    @NotNull
    String getBodyId();

    @NotNull
    PhysicsBodyType getType();

    boolean isValid();

    void destroy();

    @NotNull
    Location getLocation();

    void teleport(@NotNull Location location);

    void move(@NotNull Vector offset);

    @NotNull
    Vector getLinearVelocity();

    void setLinearVelocity(@NotNull Vector velocity);

    @NotNull
    Vector getAngularVelocity();

    void setAngularVelocity(@NotNull Vector velocity);

    void addImpulse(@NotNull Vector impulse);

    void addTorque(@NotNull Vector torque);

    void setFriction(float friction);

    float getFriction();

    void setRestitution(float restitution);

    float getRestitution();

    void setGravityFactor(float factor);

    float getGravityFactor();

    void activate();

    void deactivate();

    boolean isActive();

    void setMotionType(@NotNull MotionType motionType);

    @NotNull
    MotionType getMotionType();
}
