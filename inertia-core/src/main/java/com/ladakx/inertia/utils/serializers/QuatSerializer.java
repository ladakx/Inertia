package com.ladakx.inertia.utils.serializers;

import com.github.stephengold.joltjni.Quat;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Serialize Quaternion (Jolt Quat) to/from config/строки
 */
public class QuatSerializer {

	/**
	 * Створити Quat з кутів (у радіанах).
	 *
	 * @param pitch кут навколо X (radians)
	 * @param yaw   кут навколо Y (radians)
	 * @param roll  кут навколо Z (radians)
	 * @return кватерніон Jolt
	 */
	public static Quat serialize(float pitch, float yaw, float roll) {
		// порядок такий же, як у jME Quaternion.fromAngles: x-y-z
		return Quat.sEulerAngles(pitch, yaw, roll);
	}

	/**
	 * Створити Quat зі строки "pitch yaw roll" (в градусах).
	 *
	 * @param array строка формату "pitch yaw roll" або з комами/пробілами
	 * @return кватерніон Jolt
	 */
	public static Quat serialize(String array) {
		String[] arr = array.split("[,\\s]+");
		return serialize(
				(float) Math.toRadians(Float.parseFloat(arr[0])),
				(float) Math.toRadians(Float.parseFloat(arr[1])),
				(float) Math.toRadians(Float.parseFloat(arr[2]))
		);
	}

	/**
	 * Прочитати кути з конфига і повернути Quat.
	 * Якщо path НЕ є секцією – очікується строка "pitch yaw roll" у градусах.
	 * Якщо це секція – беруться поля pitch/yaw/roll у градусах.
	 *
	 * @param path шлях у конфігу
	 * @param cfg  конфіг
	 * @return кватерніон Jolt
	 */
	public static Quat serialize(String path, FileConfiguration cfg) {
		if (!cfg.isConfigurationSection(path)) {
			String[] arr = cfg.getString(path, "0.0 0.0 0.0").split("[,\\s]+");
			return serialize(
					(float) Math.toRadians(Float.parseFloat(arr[0])),
					(float) Math.toRadians(Float.parseFloat(arr[1])),
					(float) Math.toRadians(Float.parseFloat(arr[2]))
			);
		} else {
			return serialize(
					(float) Math.toRadians(cfg.getDouble(path + ".pitch", 0.0D)),
					(float) Math.toRadians(cfg.getDouble(path + ".yaw", 0.0D)),
					(float) Math.toRadians(cfg.getDouble(path + ".roll", 0.0D))
			);
		}
	}

	/**
	 * Повернути строку "pitch yaw roll" у радіанах з конфига
	 * (поведінка збережена як у твоїй оригінальній версії).
	 *
	 * @param path шлях у конфігу
	 * @param cfg  конфіг
	 * @return "pitch yaw roll" у радіанах
	 */
	public static String getStringXYZ(String path, FileConfiguration cfg) {
		float pitch, yaw, roll;

		if (!cfg.isConfigurationSection(path)) {
			String[] arr = cfg.getString(path, "0.0 0.0 0.0").split("[,\\s]+");
			pitch = (float) Math.toRadians(Float.parseFloat(arr[0]));
			yaw   = (float) Math.toRadians(Float.parseFloat(arr[1]));
			roll  = (float) Math.toRadians(Float.parseFloat(arr[2]));
		} else {
			pitch = (float) Math.toRadians(cfg.getDouble(path + ".pitch", 0.0D));
			yaw   = (float) Math.toRadians(cfg.getDouble(path + ".yaw", 0.0D));
			roll  = (float) Math.toRadians(cfg.getDouble(path + ".roll", 0.0D));
		}

		return pitch + " " + yaw + " " + roll;
	}
}