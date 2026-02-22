package com.ladakx.inertia.api.config;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record ConfigIssue(@NotNull String path, @NotNull String message) {
    public ConfigIssue {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }
}

