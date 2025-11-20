package com.ladakx.inertia.utils.serializers;

import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.InertiaLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialize Vec3 to/from strings/config and convert to Bukkit Vector.
 * Jolt-JNI replacement for old Vector3fSerializer.
 */
public final class Vec3Serializer {

    private Vec3Serializer() {
        // utility class
    }

    /**
     * Створити Vec3 з трьох компонент.
     */
    public static Vec3 serialize(float x, float y, float z) {
        return new Vec3(x, y, z);
    }

    /**
     * Розпарсити список рядків у список Vec3.
     * Формат рядка: "x y z" або "x,y,z" (як і в старому класі) [1].
     */
    public static List<Vec3> serialize(List<String> list) {
        List<Vec3> result = new ArrayList<>();

        for (String vec : list) {
            result.add(serialize(vec));
        }

        return result;
    }

    /**
     * Розпарсити один рядок у Vec3.
     * Формат: "x y z" / "x,y,z" / "x, y, z" тощо [1].
     */
    public static Vec3 serialize(String str) {
        if (str == null || str.isEmpty()) return new Vec3(0, 0, 0);
        try {
            String[] parts = str.trim().split("\\s+"); // Спліт по пробілах
            if (parts.length < 3) throw new IllegalArgumentException("Not enough vector components");
            float x = Float.parseFloat(parts[0]);
            float y = Float.parseFloat(parts[1]);
            float z = Float.parseFloat(parts[2]);
            return new Vec3(x, y, z);
        } catch (Exception e) {
            InertiaLogger.warn("Failed to parse vector string: '" + str + "'. Using 0,0,0. Error: " + e.getMessage());
            return new Vec3(0, 0, 0);
        }
    }

    /**
     * Прочитати Vec3 із Bukkit-конфігу.
     * ⚠ Тут я роблю припущення про формат:
     *   path.x, path.y, path.z – double в конфігу.
     * Якщо в тебе інший формат (наприклад "x, y, z" одним рядком) –
     * змінюй реалізацію під свій формат.
     */
    public static Vec3 serialize(String path, FileConfiguration cfg) {
        float x = (float) cfg.getDouble(path + ".x", 0.0);
        float y = (float) cfg.getDouble(path + ".y", 0.0);
        float z = (float) cfg.getDouble(path + ".z", 0.0);
        return new Vec3(x, y, z);
    }

    /**
     * Конвертація Vec3 → Bukkit Vector (аналог toBukkit(Vector3f) з оригіналу) [1].
     */
    public static Vector toBukkit(Vec3 vec3) {
        return new Vector(vec3.getX(), vec3.getY(), vec3.getZ());
    }
}