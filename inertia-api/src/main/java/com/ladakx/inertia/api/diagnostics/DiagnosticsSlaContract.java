package com.ladakx.inertia.api.diagnostics;

import java.util.Objects;

public record DiagnosticsSlaContract(int updateFrequencyHz,
                                     int maxCollectorCostMicros,
                                     String updateThread,
                                     int percentileWindowTicks) {
    public DiagnosticsSlaContract {
        Objects.requireNonNull(updateThread, "updateThread");
    }
}
