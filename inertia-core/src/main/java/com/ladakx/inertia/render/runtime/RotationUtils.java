package com.ladakx.inertia.render.runtime;

import org.bukkit.util.EulerAngle;
import org.joml.Quaternionf;

/**
 * Утиліти для роботи з кватерніонами/Ейлеровими кутами.
 */
public final class RotationUtils {

    private RotationUtils() {
    }

    /**
     * Конвертація кватерніона в EulerAngle (XYZ в радіанах).
     */
    public static EulerAngle toEulerAngle(Quaternionf q) {
        // Стандартні формули для yaw/pitch/roll при Y-up.
        float x = q.x;
        float y = q.y;
        float z = q.z;
        float w = q.w;

        // Roll (Z)
        double sinr_cosp = 2.0 * (w * x + y * z);
        double cosr_cosp = 1.0 - 2.0 * (x * x + y * y);
        double roll = Math.atan2(sinr_cosp, cosr_cosp);

        // Pitch (X)
        double sinp = 2.0 * (w * y - z * x);
        double pitch;
        if (Math.abs(sinp) >= 1) {
            pitch = Math.copySign(Math.PI / 2.0, sinp);
        } else {
            pitch = Math.asin(sinp);
        }

        // Yaw (Y)
        double siny_cosp = 2.0 * (w * z + x * y);
        double cosy_cosp = 1.0 - 2.0 * (y * y + z * z);
        double yaw = Math.atan2(siny_cosp, cosy_cosp);

        // Bukkit EulerAngle: X, Y, Z (рад)
        return new EulerAngle(pitch, yaw, roll);
    }

    /**
     * Конвертація кватерніона в yaw/pitch для Entity (в градусах).
     *
     * @return масив [yaw, pitch]
     */
    public static float[] toYawPitchDegrees(Quaternionf q) {
        float x = q.x;
        float y = q.y;
        float z = q.z;
        float w = q.w;

        // Pitch (X)
        double sinp = 2.0 * (w * y - z * x);
        double pitch;
        if (Math.abs(sinp) >= 1) {
            pitch = Math.copySign(Math.PI / 2.0, sinp);
        } else {
            pitch = Math.asin(sinp);
        }

        // Yaw (Y)
        double siny_cosp = 2.0 * (w * z + x * y);
        double cosy_cosp = 1.0 - 2.0 * (y * y + z * z);
        double yaw = Math.atan2(siny_cosp, cosy_cosp);

        return new float[]{
                (float) Math.toDegrees(yaw),
                (float) Math.toDegrees(pitch)
        };
    }
}