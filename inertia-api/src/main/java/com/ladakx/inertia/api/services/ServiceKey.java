package com.ladakx.inertia.api.services;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A stable, namespaced identifier for a service.
 * <p>
 * Use keys instead of raw classes to avoid accidental collisions and to support multiple implementations.
 */
public record ServiceKey<T>(@NotNull String id, @NotNull Class<T> type) {
    public ServiceKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (id.isBlank()) {
            throw new IllegalArgumentException("ServiceKey.id must not be blank");
        }
    }
}

