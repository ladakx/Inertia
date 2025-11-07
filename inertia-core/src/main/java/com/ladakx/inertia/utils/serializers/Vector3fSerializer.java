package com.ladakx.inertia.utils.serializers;

import com.jme3.math.Vector3f;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialize Vector3f to string and back
 */
public class Vector3fSerializer {

    /**
     * Serialize Vector3f to string
     * @param x The x value
     * @param y The y value
     * @param z The z value
     * @return The serialized Vector3f
     */
    public static Vector3f serialize(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    /**
     * Serialize Vector3f to string
     * @param list The list of Vector3f
     * @return The serialized list of Vector3f
     */
    public static List<Vector3f> serialize(List<String> list) {
        List<Vector3f> result = new ArrayList<>();

        for (String vec : list) {
            result.add(serialize(vec));    
        }

        return result;
    }

    /**
     * Serialize Vector3f to string
     * @param string The string to serialize
     * @return The serialized Vector3f
     */
    public static Vector3f serialize(String string) {
        String[] arr = string.split("[,\\s]+");
        return new Vector3f(
                Float.parseFloat(arr[0]),
                Float.parseFloat(arr[1]),
                Float.parseFloat(arr[2])
        );
    }

    /**
     * Serialize Vector3f to string
     * @param path The path to serialize
     * @param cfg The configuration to serialize
     * @return The serialized Vector3f
     */
    public static Vector3f serialize(String path, FileConfiguration cfg) {
        if (!cfg.isConfigurationSection(path)) {
            String[] arr = cfg.getString(path, "0.0 0.0 0.0").split("[,\\s]+");
            return new Vector3f(
                    Float.parseFloat(arr[0]),
                    Float.parseFloat(arr[1]),
                    Float.parseFloat(arr[2])
            );
        } else if (cfg.contains(path+".x") && cfg.contains(path+".y") && cfg.contains(path+".z")) {
            return new Vector3f(
                    (float) cfg.getDouble(path+".x", 0.0),
                    (float) cfg.getDouble(path+".y", 0.0),
                    (float) cfg.getDouble(path+".z", 0.0)
            );
        }

        return new Vector3f();
    }

    /**
     * Serialize Vector3f to string
     * @param vector3f The Vector3f to serialize
     * @return The serialized string
     */
    public static org.joml.Vector3f toJOML(Vector3f vector3f) {
        return new org.joml.Vector3f(vector3f.getX(), vector3f.getY(), vector3f.getZ());
    }

    /**
     * Serialize Vector3f to string
     * @param vector3f The Vector3f to serialize
     * @return The serialized string
     */
    public static Vector toBukkit(Vector3f vector3f) {
        return new Vector(vector3f.getX(), vector3f.getY(), vector3f.getZ());
    }
}
