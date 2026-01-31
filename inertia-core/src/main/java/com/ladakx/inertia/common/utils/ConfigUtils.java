package com.ladakx.inertia.common.utils;

public class ConfigUtils {

    private ConfigUtils() {
        // Utility class
    }

    public static int parseIntSafe(String str, int def) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static float parseFloat(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }
}
