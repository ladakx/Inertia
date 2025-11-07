package com.ladakx.inertia.utils.serializers;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Serialize Quaternion to string and back
 */
public class QuaternionSerializer {

	/**
	 * Serialize Quaternion to string
	 * @param pitch The pitch value
	 * @param yaw The yaw value
	 * @param roll The roll value
	 * @return The serialized Quaternion
	 */
	public static Quaternion serialize(float pitch, float yaw, float roll) {
		return new Quaternion().fromAngles(pitch, yaw, roll);
	}

	/**
	 * Serialize Quaternion to string
	 * @param array The array of Quaternion. The array must be in the format of pitch, yaw, roll
	 * @return The serialized array of Quaternion
	 */
	public static Quaternion serialize(String array) {
		String[] arr = array.split("[,\\s]+");;
		return serialize(
				FastMath.toRadians(Float.parseFloat(arr[0])),
				FastMath.toRadians(Float.parseFloat(arr[1])),
				FastMath.toRadians(Float.parseFloat(arr[2]))
				);
	}

	/**
	 * Serialize Quaternion to string from configuration
	 * @param path The path to serialize
	 * @param cfg The configuration to serialize
	 * @return The serialized Quaternion
	 */
	public static Quaternion serialize(String path, FileConfiguration cfg) {
		if (!cfg.isConfigurationSection(path)) {
			String[] arr = cfg.getString(path, "0.0 0.0 0.0").split("[,\\s]+");
			return serialize(
					FastMath.toRadians(Float.parseFloat(arr[0])),
					FastMath.toRadians(Float.parseFloat(arr[1])),
					FastMath.toRadians(Float.parseFloat(arr[2]))
				);
		} else {
			return serialize(
					FastMath.toRadians((float) cfg.getDouble(path+".pitch", 0.0D)),
					FastMath.toRadians((float) cfg.getDouble(path+".yaw", 0.0D)),
					FastMath.toRadians((float) cfg.getDouble(path+".roll", 0.0D))
				);
		}
	}

	/**
	 * Serialize Quaternion to string
	 * @param path The path to serialize
	 * @param cfg The configuration to serialize
	 * @return The serialized Quaternion
	 */
	public static String getStringXYZ(String path, FileConfiguration cfg) {
		if (!cfg.isConfigurationSection(path)) {
			String[] arr = cfg.getString(path, "0.0 0.0 0.0").split("[,\\s]+");
			return
					FastMath.toRadians(Float.parseFloat(arr[0])) + " " +
					FastMath.toRadians(Float.parseFloat(arr[1])) + " " +
					FastMath.toRadians(Float.parseFloat(arr[2]));
		} else {
			return
					FastMath.toRadians((float) cfg.getDouble(path+".pitch", 0.0D)) + " " +
					FastMath.toRadians((float) cfg.getDouble(path+".yaw", 0.0D)) + " " +
					FastMath.toRadians((float) cfg.getDouble(path+".roll", 0.0D));
		}
	}
}
