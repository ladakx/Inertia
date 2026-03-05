package com.ladakx.inertia.api.diagnostics;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface DiagnosticsService {
    @NotNull Collection<WorldHealthSnapshot> getWorldHealthSnapshots();
    @NotNull WorldHealthSnapshot getWorldHealthSnapshot(@NotNull String worldName);
    @NotNull DiagnosticsSlaContract getSlaContract();

    default @NotNull TransportDiagnosticsSnapshot getTransportDiagnosticsSnapshot() {
        return new TransportDiagnosticsSnapshot(0, 0, 0, 0.0d, 0.0d, List.of(), System.nanoTime());
    }

    default @NotNull Collection<TransportWorldSnapshot> getTransportWorldSnapshots() {
        return getTransportDiagnosticsSnapshot().worldSnapshots();
    }
}
