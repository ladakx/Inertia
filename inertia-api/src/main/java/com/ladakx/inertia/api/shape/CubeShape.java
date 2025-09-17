package com.ladakx.inertia.api.shape;

import org.bukkit.util.Vector;

/**
 * Represents a cube (or box) shape for a physical body.
 */
public class CubeShape implements InertiaShape {

    private final Vector halfExtents;

    /**
     * Creates a new cube shape with the given full size.
     *
     * @param fullSize A vector representing the full width, height, and depth of the cube.
     */
    public CubeShape(Vector fullSize) {
        // Jolt works with half-extents (distance from center to edge).
        this.halfExtents = fullSize.clone().multiply(0.5);
    }

    /**
     * Creates a new cube shape with the given full size dimensions.
     *
     * @param width The full width (X-axis).
     * @param height The full height (Y-axis).
     * @param depth The full depth (Z-axis).
     */
    public CubeShape(double width, double height, double depth) {
        this(new Vector(width, height, depth));
    }

    public Vector getHalfExtents() {
        return halfExtents;
    }
}
