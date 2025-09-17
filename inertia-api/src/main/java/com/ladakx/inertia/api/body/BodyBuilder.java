package com.ladakx.inertia.api.body;

import com.ladakx.inertia.api.shape.InertiaShape;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * A builder interface for constructing and configuring a new {@link InertiaBody}.
 * This provides a fluent API for setting up all necessary properties before creation.
 */
public interface BodyBuilder {

    /**
     * Sets the initial location (position) of the body in the world.
     * @param location The Bukkit location.
     * @return This builder instance for chaining.
     */
    BodyBuilder location(Location location);

    /**
     * Sets the initial rotation of the body.
     * @param rotation The rotation as a Quaternion.
     * @return This builder instance for chaining.
     */
    BodyBuilder rotation(Quaternionf rotation);

    /**
     * Sets the physical shape of the body.
     * @param shape The {@link InertiaShape} to use.
     * @return This builder instance for chaining.
     */
    BodyBuilder shape(InertiaShape shape);

    /**
     * Sets the motion type of the body (STATIC, KINEMATIC, or DYNAMIC).
     * @param motionType The {@link MotionType}.
     * @return This builder instance for chaining.
     */
    BodyBuilder motionType(MotionType motionType);

    /**
     * Sets the mass of the body in kilograms. Only applicable to DYNAMIC bodies.
     * @param mass The mass value.
     * @return This builder instance for chaining.
     */
    BodyBuilder mass(double mass);

    /**
     * Sets the friction coefficient of the body's surface.
     * @param friction The friction value (typically between 0.0 and 1.0).
     * @return This builder instance for chaining.
     */
    BodyBuilder friction(float friction);

    /**
     * Sets the restitution (bounciness) of the body's surface.
     * @param restitution The restitution value (0.0 for no bounce, 1.0 for perfect bounce).
     * @return This builder instance for chaining.
     */
    BodyBuilder restitution(float restitution);

    /**
     * Sets an initial linear velocity for the body.
     * @param velocity The velocity vector.
     * @return This builder instance for chaining.
     */
    BodyBuilder initialVelocity(Vector velocity);

    /**
     * Finalizes the configuration and queues the body for creation in the physics world.
     * <p>
     * The body is created asynchronously on the physics thread. The returned object can be
     * used immediately to query properties, but its position and rotation will only start
     * updating after the next physics tick.
     *
     * @return The newly created {@link InertiaBody}.
     */
    InertiaBody build();
}
