package com.ladakx.inertia.common;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.message.MessageKey;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinecraftVersions {

    private static final LinkedHashMap<String, Update> ALL_UPDATES = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Version> ALL_VERSIONS = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, List<Version>> PROTOCOL_VERSIONS = new LinkedHashMap<>();

    public static Map<String, Update> updates() {
        return Collections.unmodifiableMap(ALL_UPDATES);
    }

    public static Map<String, Version> versions() {
        return Collections.unmodifiableMap(ALL_VERSIONS);
    }

    public static final Update NETHER_UPDATE = new Update(1, 16, update -> {
        update.add(new Version(update, 0, 1, 735));
        update.add(new Version(update, 1, 1, 736));
        update.add(new Version(update, 2, 2, 751));
        update.add(new Version(update, 3, 2, 753));
        update.add(new Version(update, 4, 3, 754));
        update.add(new Version(update, 5, 3, 754));
    });

    public static final Update CAVES_AND_CLIFFS_1 = new Update(1, 17, update -> {
        update.add(new Version(update, 0, 1, 755));
        update.add(new Version(update, 1, 1, 756));
    });

    public static final Update CAVES_AND_CLIFFS_2 = new Update(1, 18, update -> {
        update.add(new Version(update, 0, 1, 757));
        update.add(new Version(update, 1, 1, 757));
        update.add(new Version(update, 2, 2, 758));
    });

    public static final Update WILD_UPDATE = new Update(1, 19, update -> {
        update.add(new Version(update, 0, 1, 759));
        update.add(new Version(update, 1, 1, 760));
        update.add(new Version(update, 2, 1, 760));
        update.add(new Version(update, 3, 2, 761));
        update.add(new Version(update, 4, 3, 762));
    });

    public static final Update TRAILS_AND_TAILS = new Update(1, 20, update -> {
        update.add(new Version(update, 0, 1, 763));
        update.add(new Version(update, 1, 1, 763));
        update.add(new Version(update, 2, 2, 764));
        update.add(new Version(update, 3, 3, 765));
        update.add(new Version(update, 4, 3, 765));
        update.add(new Version(update, 5, 4, 766));
        update.add(new Version(update, 6, 4, 766));
    });

    public static final Update TRICKY_TRIALS = new Update(1, 21, update -> {
        update.add(new Version(update, 0, 1, 767));
        update.add(new Version(update, 1, 1, 767));
        update.add(new Version(update, 2, 2, 768));
        update.add(new Version(update, 3, 2, 768));
        update.add(new Version(update, 4, 3, 769));
        update.add(new Version(update, 5, 4, 770));
        update.add(new Version(update, 6, 5, 771));
        update.add(new Version(update, 7, 6, 772));
        update.add(new Version(update, 8, 6, 772));
        update.add(new Version(update, 9, 7, 773));
        update.add(new Version(update, 10, 7, 773));
        update.add(new Version(update, 11, 8, 774));
    });

    public static final Version CURRENT = parseCurrentVersion();

    public static Version parseCurrentVersion() {
        return parseCurrentVersion(Bukkit.getVersion());
    }

    public static Version parseCurrentVersion(String versionString) {
        if (versionString == null) {
            InertiaLogger.warn("Warning: Provided version string cannot be null.");
            return null;
        }

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

        if (currentVersion == null || !ALL_VERSIONS.containsKey(currentVersion)) {
            InertiaLogger.warn("Warning: Provided version string has an unknown format.");
            return null;
        }

        return ALL_VERSIONS.get(currentVersion);
    }

    public static Version getLatestByProtocol(int networkProtocol) {
        List<Version> matchingVersions = PROTOCOL_VERSIONS.get(networkProtocol);
        if (matchingVersions == null || matchingVersions.isEmpty()) {
            InertiaLogger.warn("Warning: Attempted to fetch a version by an unknown network protocol.");
            return null;
        }
        return matchingVersions.get(matchingVersions.size() - 1);
    }

    public static List<Version> getAllByProtocol(int networkProtocol) {
        List<Version> matchingVersions = PROTOCOL_VERSIONS.get(networkProtocol);
        if (matchingVersions == null || matchingVersions.isEmpty()) {
            InertiaLogger.warn("Warning: Attempted to fetch a version by an unknown network protocol.");
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(matchingVersions);
    }

    public static boolean isCoreVersionAboveOrEqual(String version) {
        if (version == null) {
            InertiaLogger.warn("Warning: Provided version string cannot be null.");
            return false;
        }
        String coreVersion = Bukkit.getBukkitVersion().split("-")[0];
        return compareVersions(coreVersion, version) >= 0;
    }

    private static int compareVersions(String version1, String version2) {
        if (version1 == null || version2 == null) {
            return 0;
        }

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

    public static final class Update implements Comparable<Update> {
        public final int major;
        public final int minor;
        public final List<Version> versions;

        public Update(int major, int minor, VersionAdder versionAdder) {
            this.major = major;
            this.minor = minor;
            this.versions = new ArrayList<>();

            versionAdder.addVersions(this);
            ALL_UPDATES.put(toString(), this);

            for (Version version : versions) {
                ALL_VERSIONS.put(version.toString(), version);
                PROTOCOL_VERSIONS.computeIfAbsent(version.networkProtocol, k -> new ArrayList<>()).add(version);
            }
        }

        public boolean isAtLeast() {
            Version current = CURRENT;
            if (current == null) {
                return false;
            }
            return current.major > major || (current.major == major && current.minor >= minor);
        }

        public Version get(int patch) {
            if (patch < 0 || patch >= versions.size()) {
                return null;
            }
            return versions.get(patch);
        }

        @Override
        public int compareTo(Update other) {
            if (other == null) {
                return 1;
            }
            return Integer.compare(minor, other.minor);
        }

        @Override
        public String toString() {
            return major + "." + minor;
        }

        public void add(Version version) {
            if (version != null) {
                versions.add(version);
            }
        }

        @FunctionalInterface
        public interface VersionAdder {
            void addVersions(Update update);
        }
    }

    public static final class Version implements Comparable<Version> {
        public final Update update;
        public final int patch;
        public final int nmsRevision;
        public final int networkProtocol;
        public final int major;
        public final int minor;

        public Version(Update update, int patch, int nmsRevision, int networkProtocol) {
            this.update = update;
            this.patch = patch;
            this.nmsRevision = nmsRevision;
            this.networkProtocol = networkProtocol;
            this.major = update != null ? update.major : 0;
            this.minor = update != null ? update.minor : 0;
        }

        public boolean isAtLeast() {
            Version current = CURRENT;
            if (current == null) {
                return false;
            }
            return current.major > major ||
                    (current.major == major && current.minor > minor) ||
                    (current.major == major && current.minor == minor && current.patch >= patch);
        }

        public String toProtocolString() {
            return "v" + major + "_" + minor + "_R" + nmsRevision;
        }

        @Override
        public int compareTo(Version other) {
            if (other == null) {
                return 1;
            }
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