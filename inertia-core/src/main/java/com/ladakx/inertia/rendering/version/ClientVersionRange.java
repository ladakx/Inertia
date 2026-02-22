package com.ladakx.inertia.rendering.version;

import com.ladakx.inertia.common.MinecraftVersions;
import com.ladakx.inertia.common.logging.InertiaLogger;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Inclusive client version range expressed in protocol numbers.
 * Supported config forms:
 * - {@code 1.19.4-latest}
 * - {@code 1.16.5-1.19.3}
 * - {@code 1.20.1} (single version)
 */
public final class ClientVersionRange {

    private final String raw;
    private final int minProtocol;
    private final int maxProtocol;

    public ClientVersionRange(String raw, int minProtocol, int maxProtocol) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.minProtocol = minProtocol;
        this.maxProtocol = maxProtocol;
    }

    public String raw() { return raw; }
    public int minProtocol() { return minProtocol; }
    public int maxProtocol() { return maxProtocol; }

    public boolean containsProtocol(int protocol) {
        return protocol >= minProtocol && protocol <= maxProtocol;
    }

    public static @Nullable ClientVersionRange parse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        String lower;
        String upper;
        int dash = trimmed.indexOf('-');
        if (dash == -1) {
            lower = trimmed;
            upper = trimmed;
        } else {
            lower = trimmed.substring(0, dash).trim();
            upper = trimmed.substring(dash + 1).trim();
        }

        Integer min = resolveProtocol(lower, true);
        Integer max = resolveProtocol(upper, false);
        if (min == null || max == null) {
            return null;
        }
        if (min > max) {
            InertiaLogger.warn("Invalid client version range '" + trimmed + "': min > max");
            return null;
        }
        return new ClientVersionRange(trimmed, min, max);
    }

    private static @Nullable Integer resolveProtocol(String token, boolean isLower) {
        if (token == null || token.isBlank()) return null;

        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("latest")) {
            return isLower ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        MinecraftVersions.Version v = MinecraftVersions.versions().get(token.trim());
        if (v == null) {
            InertiaLogger.warn("Unknown minecraft version token '" + token + "' in client version range");
            return null;
        }
        return v.networkProtocol;
    }
}

