package com.ladakx.inertia.utils;

import org.bukkit.configuration.ConfigurationSection;

public class ConfigUtil {

    private ConfigUtil () {
        // Utility class
    }

    public static String getStringSafe(ConfigurationSection section, String path, String def) {
        if (section == null) return def;
        return section.getString(path, def);
    }

    public static int parseIntSafe(String str, int def) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
