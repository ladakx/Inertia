package com.ladakx.inertia.api.transport;

public record EngineSpec(float maxTorque,
                         float minRpm,
                         float maxRpm,
                         float inertia,
                         float angularDamping) {
    public EngineSpec {
        if (maxTorque <= 0f) throw new IllegalArgumentException("maxTorque must be > 0");
        if (minRpm < 0f) throw new IllegalArgumentException("minRpm must be >= 0");
        if (maxRpm <= minRpm) throw new IllegalArgumentException("maxRpm must be > minRpm");
        if (inertia <= 0f) throw new IllegalArgumentException("inertia must be > 0");
        if (angularDamping < 0f) throw new IllegalArgumentException("angularDamping must be >= 0");
    }
}
