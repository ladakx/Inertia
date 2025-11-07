package com.ladakx.inertia.utils.serializers;

import com.jme3.math.Vector2f;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialize Vector2f to string and back
 */
public class Vector2fSerializer {

    /**
     * Serialize Vector2f to string
     * @param x The x value
     * @param y The y value
     * @return The serialized Vector2f
     */
    public static Vector2f serialize(float x, float y) {
        return new Vector2f(x, y);
    }

    /**
     * Serialize Vector2f to string
     * @param list The list of Vector2f
     * @return The serialized list of Vector2f
     */
    public static List<Vector2f> serialize(List<String> list) {
        List<Vector2f> result = new ArrayList<>();

        for (String vec : list) {
            result.add(serialize(vec));    
        }

        return result;
    }

    /**
     * Serialize Vector2f to string
     * @param string The string to serialize
     * @return The serialized Vector2f
     */
    public static Vector2f serialize(String string) {
        String[] arr = string.split("[,\\s]+");
        return new Vector2f(
                Float.parseFloat(arr[0]),
                Float.parseFloat(arr[1])
        );
    }

    /**
     * Serialize Vector2f to string
     * @param path The path to serialize
     * @param cfg The configuration to serialize
     * @return The serialized Vector2f
     */
    public static Vector2f serialize(String path, FileConfiguration cfg) {
        if (!cfg.isConfigurationSection(path)) {
            String[] arr = cfg.getString(path, "0.0 0.0").split("[,\\s]+");
            return new Vector2f(
                    Float.parseFloat(arr[0]),
                    Float.parseFloat(arr[1])
            );
        } else if (cfg.contains(path+".x") && cfg.contains(path+".y")) {
            return new Vector2f(
                    (float) cfg.getDouble(path+".x", 0.0),
                    (float) cfg.getDouble(path+".y", 0.0)
            );
        }

        return new Vector2f();
    }

    /**
     * Deserialize Vector2f to string
     * @param vector2f The Vector2f to deserialize
     * @return The deserialized string
     */
    public static org.joml.Vector2f toJOML(Vector2f vector2f) {
        return new org.joml.Vector2f(vector2f.getX(), vector2f.getY());
    }

    /**
     * Deserialize Vector2f to string
     * @param vector2f The Vector2f to deserialize
     * @return The deserialized string
     */
    public static Vector toBukkit(Vector2f vector2f) {
        return new Vector(vector2f.getX(), vector2f.getY(), 0.0);
    }
}
