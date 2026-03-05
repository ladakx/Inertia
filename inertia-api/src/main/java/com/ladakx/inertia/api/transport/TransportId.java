package com.ladakx.inertia.api.transport;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record TransportId(@NotNull String value) {
    public TransportId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static @NotNull TransportId random() {
        return new TransportId("transport:" + UUID.randomUUID());
    }
}
