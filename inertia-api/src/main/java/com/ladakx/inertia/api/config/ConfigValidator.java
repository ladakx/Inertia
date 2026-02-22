package com.ladakx.inertia.api.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ConfigValidator {
    void validate(@NotNull FileConfiguration config) throws ConfigValidationException;
}

