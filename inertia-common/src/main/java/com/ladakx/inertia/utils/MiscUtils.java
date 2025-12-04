package com.ladakx.inertia.utils;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * A utility class containing miscellaneous utility methods.
 */
public class MiscUtils {

    private MiscUtils () {
        // Utility class
    }

    /**
     * Returns a location object with the given coordinates in the specified world.
     *
     * @param world The name of the world in which the location is located.
     * @param x The x-coordinate of the location.
     * @param y The y-coordinate of the location.
     * @param z The z-coordinate of the location.
     * @return A location object with the specified coordinates in the specified world, or null if the world does not exist or if the x, y, or z values cannot be parsed as integers.
     */
    public static Location getLocation(String world, String x, String y, String z) {
        double x_, y_, z_;
        try {
            x_ = Integer.parseInt(x);
            y_ = Integer.parseInt(y);
            z_ = Integer.parseInt(z);
        } catch (NumberFormatException e) {
            return null;
        }

        if (Bukkit.getWorld(world) == null) {
            return new Location(Bukkit.getWorld("world"), x_, y_, z_);
        }

        return new Location(Bukkit.getWorld(world), x_, y_, z_);
    }

    public static Vector lerpVec(Vector a, Vector b, double f) {
        return new Vector(
                lerp(a.getX(), b.getX(), f),
                lerp(a.getY(), b.getY(), f),
                lerp(a.getZ(), b.getZ(), f)
        );
    }

    public static double lerp(double a, double b, double f) {
        return a + f * (b - a);
    }

    public static Vec3 mul(Vec3 source, float scalar) {
        return new Vec3(source.getX() * scalar, source.getY() * scalar, source.getZ() * scalar);
    }

    public static Vec3 mul(Vec3 source, Vec3Arg other) {
        return new Vec3(source.getX() * other.getX(), source.getY() * other.getY(), source.getZ() * other.getZ());
    }
}
