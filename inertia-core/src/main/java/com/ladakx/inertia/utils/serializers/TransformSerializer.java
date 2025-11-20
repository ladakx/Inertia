package com.ladakx.inertia.utils.serializers;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;

import java.util.Map;

/**
 * Утил для парсингу Jolt-трансформації (позиція + ротація)
 * з простого key=value набору параметрів.
 *
 * Підтримувані ключі:
 *   - px, py, pz  -> локальний зсув (позиція)
 *   - rot / rotation = "pitch yaw roll" або "pitch,yaw,roll" у ГРАДУСАХ
 *   - pitch, yaw, roll -> окремо, теж у градусах
 *
 * Для ротації використовує існуючий QuatSerializer, який очікує кути в градусах
 * і всередині конвертує їх у радіани перед викликом Quat.sEulerAngles(...) [1].
 */
public final class TransformSerializer {

    private TransformSerializer() {
        // utility class
    }

    /**
     * Розпарсити позицію+ротацію з Map key=value (усі ключі мають бути в lowerCase).
     *
     * @param kv map з параметрами ("px","py","pz","rot","pitch","yaw","roll"...)
     * @return JoltTransform (Vec3 + Quat + прапорці hasPosition/hasRotation)
     */
    public static JoltTransform fromKeyValueMap(Map<String, String> kv) {
        // ---------- Позиція ----------
        String pxStr = kv.get("px");
        String pyStr = kv.get("py");
        String pzStr = kv.get("pz");

        boolean hasPosition =
                (pxStr != null) || (pyStr != null) || (pzStr != null);

        float px = (pxStr != null) ? parseFloat(pxStr) : 0f;
        float py = (pyStr != null) ? parseFloat(pyStr) : 0f;
        float pz = (pzStr != null) ? parseFloat(pzStr) : 0f;

        Vec3 position = new Vec3(px, py, pz);

        // ---------- Ротація ----------
        String rotStr = kv.get("rot");
        if (rotStr == null) {
            rotStr = kv.get("rotation");
        }

        String pitchStr = kv.get("pitch");
        String yawStr   = kv.get("yaw");
        String rollStr  = kv.get("roll");

        boolean hasRotation =
                (rotStr != null && !rotStr.isBlank())
                        || pitchStr != null
                        || yawStr != null
                        || rollStr != null;

        Quat rotation;
        if (rotStr != null && !rotStr.isBlank()) {
            // "pitch yaw roll" або "pitch,yaw,roll" у градусах [1]
            rotation = QuatSerializer.serialize(rotStr);
        } else if (pitchStr != null || yawStr != null || rollStr != null) {
            float pitchDeg = parseFloat(pitchStr != null ? pitchStr : "0");
            float yawDeg   = parseFloat(yawStr   != null ? yawStr   : "0");
            float rollDeg  = parseFloat(rollStr  != null ? rollStr  : "0");
            String triple  = pitchDeg + " " + yawDeg + " " + rollDeg;
            // QuatSerializer тут знову очікує градуси й сам робить toRadians [1]
            rotation = QuatSerializer.serialize(triple);
        } else {
            // identity-кватерніон
            rotation = new Quat(0f, 0f, 0f, 1f);
        }

        return new JoltTransform(position, rotation, hasPosition, hasRotation);
    }

    private static float parseFloat(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid float value '" + text + "' in transform definition",
                    e
            );
        }
    }

    /**
     * DTO для результату парсингу трансформації.
     */
    public record JoltTransform(
            Vec3 position,
            Quat rotation,
            boolean hasPosition,
            boolean hasRotation
    ) {
    }
}