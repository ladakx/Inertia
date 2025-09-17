package com.ladakx.inertia.api.body;

/**
 * Defines the motion behavior of a physical body.
 * This corresponds to the motion types available in the Jolt physics engine.
 */
public enum MotionType {
    /**
     * A static body that never moves. This is ideal for world geometry like the ground and buildings.
     * It does not need to be simulated and is very cheap computationally.
     */
    STATIC,

    /**
     * A kinematic body can be moved manually by setting its position and rotation,
     * but it is not affected by forces or collisions. It acts as an immovable object to other dynamic bodies.
     * Ideal for moving platforms, elevators, or doors.
     */
    KINEMATIC,

    /**
     * A dynamic body is fully simulated. It is affected by forces, gravity, and collisions with other objects.
     * This is the standard type for most interactive objects like crates, projectiles, or vehicles.
     */
    DYNAMIC
}