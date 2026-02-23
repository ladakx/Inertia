package com.ladakx.inertia.api.version;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record ApiVersion(int major, int minor, int patch) implements Comparable<ApiVersion> {

    public ApiVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("SemVer components must be non-negative");
        }
    }

    public static @NotNull ApiVersion parse(@NotNull String value) {
        Objects.requireNonNull(value, "value");
        String[] parts = value.trim().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid SemVer format: " + value);
        }
        try {
            return new ApiVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid SemVer format: " + value, ex);
        }
    }

    public boolean isAtLeast(@NotNull ApiVersion minimum) {
        Objects.requireNonNull(minimum, "minimum");
        return compareTo(minimum) >= 0;
    }

    @Override
    public int compareTo(@NotNull ApiVersion other) {
        Objects.requireNonNull(other, "other");
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public @NotNull String toString() {
        return major + "." + minor + "." + patch;
    }
}
