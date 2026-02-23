package com.ladakx.inertia.api.diagnostics;

public record TickDurationPercentiles(double p50Ms,
                                      double p90Ms,
                                      double p95Ms,
                                      double p99Ms,
                                      double maxMs) {
}
