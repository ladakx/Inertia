package com.ladakx.inertia.api.diagnostics;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface DiagnosticsService {
    @NotNull Collection<WorldHealthSnapshot> getWorldHealthSnapshots();
    @NotNull WorldHealthSnapshot getWorldHealthSnapshot(@NotNull String worldName);
    @NotNull DiagnosticsSlaContract getSlaContract();
}
