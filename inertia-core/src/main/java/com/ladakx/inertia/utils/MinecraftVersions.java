/**
 * This class was taken from the GitHub repository:
 * https://github.com/WeaponMechanics/MechanicsMain
 *
 * Original author: [CJCrafter]
 *
 * License: MIT License
 * You are free to use, modify, and distribute this code as long as this license notice is retained.
 *
 * The original license file can be found in the repository or at:
 * https://opensource.org/licenses/MIT
 *
 * Copyright (c) [2025] [CJCrafter]
 */

package com.ladakx.inertia.utils;

import org.bukkit.Bukkit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * https://www.spigotmc.org/wiki/spigot-nms-and-minecraft-versions-1-16/
 */
public class MinecraftVersions {
    private static final LinkedHashMap<String, Update> allUpdates = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Version> allVersions = new LinkedHashMap<>();

    public static Map<String, Update> updates() {
        return allUpdates;
    }

    public static Map<String, Version> versions() {
        return allVersions;
    }

    public static final Update WORLD_OF_COLOR = new Update(1, 12, update -> {
        update.add(new Version(update, 0, 1)); // 1.12
        update.add(new Version(update, 1, 1)); // 1.12.1
        update.add(new Version(update, 2, 1)); // 1.12.2
    });

    public static final Update UPDATE_AQUATIC = new Update(1, 13, update -> {
        update.add(new Version(update, 0, 1)); // 1.13
        update.add(new Version(update, 1, 2)); // 1.13.1
        update.add(new Version(update, 2, 2)); // 1.13.2
    });

    public static final Update VILLAGE_AND_PILLAGE = new Update(1, 14, update -> {
        update.add(new Version(update, 0, 1)); // 1.14
        update.add(new Version(update, 1, 1)); // 1.14.1
        update.add(new Version(update, 2, 1)); // 1.14.2
        update.add(new Version(update, 3, 1)); // 1.14.3
        update.add(new Version(update, 4, 1)); // 1.14.4
    });

    public static final Update BUZZY_BEES = new Update(1, 15, update -> {
        update.add(new Version(update, 0, 1)); // 1.15
        update.add(new Version(update, 1, 1)); // 1.15.1
        update.add(new Version(update, 2, 1)); // 1.15.2
    });

    public static final Update NETHER_UPDATE = new Update(1, 16, update -> {
        update.add(new Version(update, 0, 1)); // 1.16
        update.add(new Version(update, 1, 1)); // 1.16.1
        update.add(new Version(update, 2, 2)); // 1.16.2
        update.add(new Version(update, 3, 2)); // 1.16.3
        update.add(new Version(update, 4, 3)); // 1.16.4
        update.add(new Version(update, 5, 3)); // 1.16.5
    });

    public static final Update CAVES_AND_CLIFFS_1 = new Update(1, 17, update -> {
        update.add(new Version(update, 0, 1)); // 1.17
        update.add(new Version(update, 1, 1)); // 1.17.1
    });

    public static final Update CAVES_AND_CLIFFS_2 = new Update(1, 18, update -> {
        update.add(new Version(update, 0, 1)); // 1.18
        update.add(new Version(update, 1, 1)); // 1.18.1
        update.add(new Version(update, 2, 2)); // 1.18.2
    });

    public static final Update WILD_UPDATE = new Update(1, 19, update -> {
        update.add(new Version(update, 0, 1)); // 1.19
        update.add(new Version(update, 1, 1)); // 1.19.1
        update.add(new Version(update, 2, 1)); // 1.19.2
        update.add(new Version(update, 3, 2)); // 1.19.3
        update.add(new Version(update, 4, 3)); // 1.19.4
    });

    public static final Update TRAILS_AND_TAILS = new Update(1, 20, update -> {
        update.add(new Version(update, 0, 1)); // 1.20
        update.add(new Version(update, 1, 1)); // 1.20.1
        update.add(new Version(update, 2, 2)); // 1.20.2
        update.add(new Version(update, 3, 3)); // 1.20.3
        update.add(new Version(update, 4, 3)); // 1.20.4
        update.add(new Version(update, 5, 4)); // 1.20.5
        update.add(new Version(update, 6, 4)); // 1.20.6
    });

    public static final Update TRICKY_TRIALS = new Update(1, 21, update -> {
        update.add(new Version(update, 0, 1)); // 1.21
        update.add(new Version(update, 1, 1)); // 1.21.1
        update.add(new Version(update, 2, 2)); // 1.21.2
        update.add(new Version(update, 3, 2)); // 1.21.3
        update.add(new Version(update, 4, 3)); // 1.21.4
    });

    // Lazy initialization for CURRENT in a static block
    public static final Version CURRENT = parseCurrentVersion();

    public static Version parseCurrentVersion() {
        return parseCurrentVersion(Bukkit.getVersion());
    }

    public static Version parseCurrentVersion(String versionString) {
        String currentVersion = null;
        Pattern pattern = Pattern.compile("\\d+\\.\\d+\\.\\d+");
        Matcher matcher = pattern.matcher(versionString);
        if (matcher.find()) {
            currentVersion = matcher.group();
        } else {
            pattern = Pattern.compile("\\d+\\.\\d+");
            matcher = pattern.matcher(versionString);
            if (matcher.find()) {
                currentVersion = matcher.group() + ".0";
            }
        }

        if (currentVersion == null || !allVersions.containsKey(currentVersion)) {
            throw new IllegalStateException("Invalid version: " + currentVersion);
        }
        return allVersions.get(currentVersion);
    }

    /** Check minecraft server version */
    public static boolean isCoreVersionAboveOrEqual(String version) {
        String coreVersion = Bukkit.getBukkitVersion().split("-")[0];

        int comprison = compareVersions(coreVersion, version);
        return comprison >= 0;
    }



    /** Compare versions */
    private static int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            int part1 = (i < parts1.length) ? Integer.parseInt(parts1[i]) : 0;
            int part2 = (i < parts2.length) ? Integer.parseInt(parts2[i]) : 0;

            if (part1 < part2) {
                return -1;
            } else if (part1 > part2) {
                return 1;
            }
        }

        return 0;
    }

    public static class Update implements Comparable<Update> {
        public final int major;
        public final int minor;
        public final List<Version> versions;

        public Update(int major, int minor, VersionAdder versionAdder) {
            this.major = major;
            this.minor = minor;
            this.versions = new ArrayList<>();

            versionAdder.addVersions(this);
            allUpdates.put(toString(), this);

            for (Version version : versions) {
                allVersions.put(version.toString(), version);
            }
        }

        public boolean isAtLeast() {
            Version current = CURRENT;
            return current.major > major || (current.major == major && current.minor >= minor);
        }

        public Version get(int patch) {
            return versions.get(patch);
        }

        @Override
        public int compareTo(Update other) {
            return Integer.compare(minor, other.minor);
        }

        @Override
        public String toString() {
            return major + "." + minor;
        }

        @FunctionalInterface
        public interface VersionAdder {
            void addVersions(Update update);
        }

        public void add(Version version) {
            versions.add(version);
        }
    }

    public static class Version implements Comparable<Version> {
        public final Update update;
        public final int patch;
        public final int protocol;
        public final int major;
        public final int minor;

        public Version(Update update, int patch, int protocol) {
            this.update = update;
            this.patch = patch;
            this.protocol = protocol;
            this.major = update.major;
            this.minor = update.minor;
        }

        public boolean isAtLeast() {
            Version current = CURRENT;
            return current.major > major ||
                    (current.major == major && current.minor > minor) ||
                    (current.major == major && current.minor == minor && current.patch >= patch);
        }

        public String toProtocolString() {
            return "v" + major + "_" + minor + "_R" + protocol;
        }

        @Override
        public int compareTo(Version other) {
            if (minor != other.minor) {
                return Integer.compare(minor, other.minor);
            }
            return Integer.compare(patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}

