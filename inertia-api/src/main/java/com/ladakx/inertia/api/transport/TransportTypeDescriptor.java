package com.ladakx.inertia.api.transport;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable transport type metadata.
 */
public record TransportTypeDescriptor(@NotNull TransportTypeKey key, @NotNull String displayName) {
    public TransportTypeDescriptor {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
    }
}

