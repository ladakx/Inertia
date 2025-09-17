package com.ladakx.inertia.api.math;

import org.bukkit.util.Vector;

/**
 * Utility class for mathematical operations and conversions between Bukkit and Jolt data types.
 */
public final class InertiaMath {

    private InertiaMath() {
        // Private constructor to prevent instantiation
    }

    /**
     * Converts a Jolt float array (float[3]) to a Bukkit Vector.
     *
     * @param joltVector The float array representing the vector in Jolt.
     * @return A new Bukkit Vector instance.
     */
    public static Vector toBukkit(float[] joltVector) {
        if (joltVector == null || joltVector.length < 3) {
            return new Vector(0, 0, 0);
        }
        return new Vector(joltVector[0], joltVector[1], joltVector[2]);
    }

    /**
     * Converts a Bukkit Vector to a Jolt float array (float[3]).
     *
     * @param bukkitVector The Bukkit Vector to convert.
     * @return A new float array representing the vector for Jolt.
     */
    public static float[] toJolt(Vector bukkitVector) {
        if (bukkitVector == null) {
            return new float[]{0, 0, 0};
        }
        return new float[]{(float) bukkitVector.getX(), (float) bukkitVector.getY(), (float) bukkitVector.getZ()};
    }
}