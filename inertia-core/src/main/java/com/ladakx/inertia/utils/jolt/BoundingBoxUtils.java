package com.ladakx.inertia.utils.jolt;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstAaBox;

/**
 * Utility class for AaBox (Jolt-JNI)
 */
public final class BoundingBoxUtils {

    private BoundingBoxUtils() {
        // utility class
    }

    /**
     * Inflate the bounding box by the given value on all axes.
     *
     * @param box   The box to inflate
     * @param value The value to inflate by
     */
    public static void inflate(AaBox box, float value) {
        inflate(box, value, value, value);
    }

    /**
     * Inflate the bounding box by the given values on each axis.
     *
     * @param box The box to inflate
     * @param x   The X value to inflate by
     * @param y   The Y value to inflate by
     * @param z   The Z value to inflate by
     */
    public static void inflate(AaBox box, float x, float y, float z) {
        // expandBy збільшує half-extent на вказані величини
        box.expandBy(new Vec3(x, y, z));
    }

    /**
     * Check if the bounding box intersects with another bounding box.
     *
     * @param box1 The first bounding box
     * @param box2 The second bounding box
     * @return True if the bounding boxes intersect
     */
    public static boolean intersects(ConstAaBox box1, ConstAaBox box2) {
        // overlaps вже реалізує AABB-перетин
        return box1.overlaps(box2);
    }
}