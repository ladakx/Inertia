package com.ladakx.inertia.utils.serializers;

import com.github.stephengold.joltjni.Vec3;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Serialize Location to string and back
 */
public class LocationSerializer {

    private LocationSerializer () {
        // utility class
    }

    /**
     * Serialize Location to string
     * @param location The location to serialize
     * @return The serialized Location
     */
    public static Vec3 serialize(Location location) {
        return new Vec3((float) location.getX(), (float) location.getY(), (float) location.getZ());
    }

    /**
     * Serialize Location to string
     * @param world The world of the location
     * @param location The location to serialize
     * @return The serialized Location
     */
    public static Location serialize(World world, Vec3 location) {
        return new Location(world, location.getX(), location.getY(), location.getZ(), 0.0F, 0.0F);
    }
}
