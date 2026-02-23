package com.ladakx.inertia.api.diagnostics;

public record BodyCounters(int activeBodies,
                           int totalBodies,
                           int staticBodies,
                           int maxBodies) {
}
