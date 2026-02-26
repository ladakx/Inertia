package com.ladakx.inertia.api.body;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface PhysicsBody {

    @NotNull
    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    String getBodyId();

    @NotNull
    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    PhysicsBodyType getType();

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    boolean isValid();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void destroy();

    @NotNull
    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    Location getLocation();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void teleport(@NotNull Location location);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void move(@NotNull Vector offset);

    @NotNull
    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    Vector getLinearVelocity();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void setLinearVelocity(@NotNull Vector velocity);

    @NotNull
    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    Vector getAngularVelocity();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void setAngularVelocity(@NotNull Vector velocity);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void addImpulse(@NotNull Vector impulse);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void addTorque(@NotNull Vector torque);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void setFriction(float friction);

    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    float getFriction();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void setRestitution(float restitution);

    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    float getRestitution();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void setGravityFactor(float factor);

    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    float getGravityFactor();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void activate();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void deactivate();

    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    boolean isActive();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void setMotionType(@NotNull MotionType motionType);

    @NotNull
    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    MotionType getMotionType();
}
