package com.ladakx.inertia.api.body;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * Represents a single physical body within the Inertia simulation world.
 * This interface provides a high-level, thread-safe way to interact with a body's state and properties.
 */
public interface InertiaBody {

    /**
     * Gets the unique identifier for this body.
     * This ID is used to track and manage the body within the PhysicsManager.
     *
     * @return The body's unique ID.
     */
    int getId();

    /**
     * Gets the current motion type of the body.
     *
     * @return The {@link MotionType} of the body.
     */
    MotionType getMotionType();

    /**
     * Retrieves the current position of the body in world coordinates.
     * <p>
     * Note: This method is thread-safe and reads from the latest synced physics state.
     * It does not block the physics thread.
     *
     * @return A {@link Vector} representing the body's position.
     */
    Vector getPosition();

    /**
     * Retrieves the current rotation of the body.
     * <p>
     * Note: This method is thread-safe and reads from the latest synced physics state.
     * It does not block the physics thread.
     *
     * @return A {@link Quaternionf} representing the body's rotation.
     */
    Quaternionf getRotation();

    /**
     * Applies an instantaneous change in velocity to the center of mass of the body.
     * This is thread-safe and will be executed on the next physics tick.
     *
     * @param impulse The impulse vector to apply.
     */
    void applyImpulse(Vector impulse);
}