package com.ladakx.inertia.api.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal schema helper for validating YAML configs with readable error lists.
 */
public final class ConfigSchema implements ConfigValidator {

    private final List<java.util.function.Consumer<ValidationContext>> rules = new ArrayList<>();

    public @NotNull ConfigSchema requireSection(@NotNull String path) {
        Objects.requireNonNull(path, "path");
        rules.add(ctx -> {
            ConfigurationSection sec = ctx.config.getConfigurationSection(path);
            if (sec == null) ctx.issue(path, "required section is missing");
        });
        return this;
    }

    public @NotNull ConfigSchema requireString(@NotNull String path) {
        Objects.requireNonNull(path, "path");
        rules.add(ctx -> {
            if (!ctx.config.isString(path)) ctx.issue(path, "required string is missing or not a string");
        });
        return this;
    }

    public @NotNull ConfigSchema requireInt(@NotNull String path) {
        Objects.requireNonNull(path, "path");
        rules.add(ctx -> {
            if (!ctx.config.isInt(path)) ctx.issue(path, "required int is missing or not an int");
        });
        return this;
    }

    public @NotNull ConfigSchema requireBoolean(@NotNull String path) {
        Objects.requireNonNull(path, "path");
        rules.add(ctx -> {
            if (!ctx.config.isBoolean(path)) ctx.issue(path, "required boolean is missing or not a boolean");
        });
        return this;
    }

    public @NotNull ConfigSchema requireList(@NotNull String path) {
        Objects.requireNonNull(path, "path");
        rules.add(ctx -> {
            if (!ctx.config.isList(path)) ctx.issue(path, "required list is missing or not a list");
        });
        return this;
    }

    @Override
    public void validate(@NotNull FileConfiguration config) throws ConfigValidationException {
        Objects.requireNonNull(config, "config");
        ValidationContext ctx = new ValidationContext(config);
        for (var rule : rules) rule.accept(ctx);
        if (!ctx.issues.isEmpty()) {
            throw new ConfigValidationException(ctx.issues);
        }
    }

    private static final class ValidationContext {
        private final FileConfiguration config;
        private final List<ConfigIssue> issues = new ArrayList<>();

        private ValidationContext(FileConfiguration config) {
            this.config = config;
        }

        private void issue(String path, String message) {
            issues.add(new ConfigIssue(path, message));
        }
    }
}

