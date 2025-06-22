package com.ladakx.inertia.api.shape;

import org.joml.Vector3f; // Правильний імпорт

/**
 * Represents a box shape with given half-extents.
 */
public class BoxShape implements BodyShape {

    private final Vector3f halfExtents;

    /**
     * Creates a new box shape.
     * @param halfExtents A vector representing half the size of the box along each axis.
     * For a 2x2x2 cube, this would be (1, 1, 1).
     */
    public BoxShape(Vector3f halfExtents) {
        this.halfExtents = halfExtents;
    }

    public Vector3f getHalfExtents() {
        return new Vector3f(halfExtents); // Повертаємо копію для безпеки
    }
}