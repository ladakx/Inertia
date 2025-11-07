package com.ladakx.inertia.utils.serializers;

import com.jme3.math.Vector3f;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Serialize Location to string and back
 */
public class LocationSerializer {

    /**
     * Serialize Location to string
     * @param location The location to serialize
     * @return The serialized Location
     */
    public static Vector3f serialize(Location location) {
        return new Vector3f((float) location.getX(), (float) location.getY(), (float) location.getZ());
    }

    /**
     * Serialize Location to string
     * @param world The world of the location
     * @param location The location to serialize
     * @return The serialized Location
     */
    public static Location serialize(World world, Vector3f location) {
        return new Location(world, location.getX(), location.getY(), location.getZ(), 0.0F, 0.0F);
    }
}
