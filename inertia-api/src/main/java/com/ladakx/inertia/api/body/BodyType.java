/* Original project path: inertia-api/src/main/java/com/ladakx/inertia/api/body/BodyType.java */

package com.ladakx.inertia.api.body;

/**
 * Defines the motion type of a rigid body.
 */
public enum BodyType {
    /**
     * A non-movable body. It has an infinite mass and will not react to forces.
     * It is used for static world geometry.
     */
    STATIC(0),
    /**
     * A body that can be moved by forces and has a finite mass.
     * This is the standard type for dynamic objects.
     */
    DYNAMIC(1),
    /**
     * A body that is moved by explicitly setting its velocity, not by forces.
     * It is not affected by gravity or collisions with other bodies but can push other dynamic bodies.
     * Useful for moving platforms, elevators, etc.
     */
    KINEMATIC(2);

    private final int internalId;

    BodyType(int internalId) {
        this.internalId = internalId;
    }

    /**
     * Gets the internal integer ID used by the native physics engine.
     * @return The integer ID.
     */
    public int getInternalId() {
        return internalId;
    }
}