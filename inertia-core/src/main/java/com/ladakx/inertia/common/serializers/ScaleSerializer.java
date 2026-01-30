package com.ladakx.inertia.common.serializers;

import org.bukkit.configuration.file.FileConfiguration;
import org.joml.Vector3f;

/**
 * Serialize Vector3f to string and back
 */
public class ScaleSerializer {

	private ScaleSerializer() {
		// utility class
	}

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
	 * @param array The array of Vector3f
	 * @return The serialized array of Vector3f
	 */
	public static Vector3f serialize(String array) {
		String[] arr = array.split("[,\\s]+");
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
			String[] arr = cfg.getString(path, "1.0 1.0 1.0").split("[,\\s]+");
			return new Vector3f(
					Float.parseFloat(arr[0]),
					Float.parseFloat(arr[1]),
					Float.parseFloat(arr[2])
			);
		} else if (cfg.contains(path + ".x") && cfg.contains(path + ".y") && cfg.contains(path + ".z")) {
			return new Vector3f(
					(float) cfg.getDouble(path + ".x", 1.0),
					(float) cfg.getDouble(path + ".y", 1.0),
					(float) cfg.getDouble(path + ".z", 1.0)
			);
		}

        return new Vector3f();
    }
}
