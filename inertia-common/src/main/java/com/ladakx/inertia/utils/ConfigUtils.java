package com.ladakx.inertia.utils;

import org.bukkit.configuration.ConfigurationSection;

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
