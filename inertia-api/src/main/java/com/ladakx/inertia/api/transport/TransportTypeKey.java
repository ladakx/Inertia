package com.ladakx.inertia.api.transport;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/**
 * Stable, namespaced identifier for a transport type (e.g. {@code "myplugin:car"}).
 */
public record TransportTypeKey(@NotNull String namespace, @NotNull String value) {

    public TransportTypeKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(value, "value");
        if (!isValidPart(namespace)) {
            throw new IllegalArgumentException("Invalid namespace: " + namespace);
        }
        if (!isValidPart(value)) {
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }

    public static @NotNull TransportTypeKey parse(@NotNull String raw) {
        Objects.requireNonNull(raw, "raw");
        String s = raw.trim();
        int idx = s.indexOf(':');
        if (idx <= 0 || idx == s.length() - 1) {
            throw new IllegalArgumentException("Invalid transport key (expected namespace:value): " + raw);
        }
        return new TransportTypeKey(s.substring(0, idx), s.substring(idx + 1));
    }

    public @NotNull String asString() {
        return namespace + ":" + value;
    }

    @Override
    public @NotNull String toString() {
        return asString();
    }

    private static boolean isValidPart(@NotNull String s) {
        if (s.isBlank()) return false;
        String v = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (!ok) return false;
        }
        return true;
    }
}

