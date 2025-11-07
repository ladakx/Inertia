package com.ladakx.inertia.utils.bullet;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;

/**
 * Utility class for BoundingBox
 */
public class BoundingBoxUtils {

    /**
     * Inflate the bounding box by the given value.
     * @param boundingBox The bounding box to inflate
     * @param value The value to inflate by
     */
    public static void inflate(BoundingBox boundingBox, float value) {
        inflate(boundingBox, value, value, value);
    }

    /**
     * Inflate the bounding box by the given value.
     * @param boundingBox The bounding box to inflate
     * @param x The x value to inflate by
     * @param y The y value to inflate by
     * @param z The z value to inflate by
     */
    public static void inflate(BoundingBox boundingBox, float x, float y, float z) {
        boundingBox.setXExtent(boundingBox.getXExtent() + x);
        boundingBox.setYExtent(boundingBox.getYExtent() + y);
        boundingBox.setZExtent(boundingBox.getZExtent() + z);
    }

    /**
     * Check if the bounding box intersects with another bounding box.
     * @param boundingBox1 The first bounding box
     * @param boundingBox2 The second bounding box
     * @return True if the bounding boxes intersect
     */
    public static boolean intersects(BoundingBox boundingBox1, BoundingBox boundingBox2) {
        Vector3f min1 = boundingBox1.getMin(null);
        Vector3f max1 = boundingBox1.getMax(null);
        Vector3f min2 = boundingBox2.getMin(null);
        Vector3f max2 = boundingBox2.getMax(null);

        return min1.getX() < max2.getX() && max1.getX() > min2.getX() && min1.getY() < max2.getY() && max1.getY() > min2.getY() && min1.getZ() < max2.getZ() && max1.getZ() > min2.getZ();
    }
}
