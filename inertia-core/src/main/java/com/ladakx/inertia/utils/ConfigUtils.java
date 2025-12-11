package com.ladakx.inertia.utils;

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
}
