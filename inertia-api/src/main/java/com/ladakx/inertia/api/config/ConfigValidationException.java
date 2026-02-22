package com.ladakx.inertia.api.config;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ConfigValidationException extends RuntimeException {

    private final List<ConfigIssue> issues;

    public ConfigValidationException(@NotNull List<ConfigIssue> issues) {
        super(buildMessage(issues));
        Objects.requireNonNull(issues, "issues");
        this.issues = Collections.unmodifiableList(List.copyOf(issues));
    }

    public @NotNull List<ConfigIssue> issues() {
        return issues;
    }

    private static String buildMessage(List<ConfigIssue> issues) {
        if (issues == null || issues.isEmpty()) return "Config validation failed";
        StringBuilder sb = new StringBuilder("Config validation failed (").append(issues.size()).append(" issue(s)):");
        for (ConfigIssue issue : issues) {
            if (issue == null) continue;
            sb.append("\n - ").append(issue.path()).append(": ").append(issue.message());
        }
        return sb.toString();
    }
}

