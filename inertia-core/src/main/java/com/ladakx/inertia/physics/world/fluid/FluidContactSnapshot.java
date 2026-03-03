package com.ladakx.inertia.physics.world.fluid;

import java.util.Objects;

final class FluidContactSnapshot {
    static final FluidContactSnapshot EMPTY = new FluidContactSnapshot(new int[0], new byte[0], new float[0]);

    final int[] bodyIds;
    final byte[] mediumIds;
    final float[] submersion;

    FluidContactSnapshot(int[] bodyIds, byte[] mediumIds, float[] submersion) {
        this.bodyIds = Objects.requireNonNull(bodyIds, "bodyIds");
        this.mediumIds = Objects.requireNonNull(mediumIds, "mediumIds");
        this.submersion = Objects.requireNonNull(submersion, "submersion");
        if (bodyIds.length != mediumIds.length || bodyIds.length != submersion.length) {
            throw new IllegalArgumentException("Snapshot arrays must have identical lengths");
        }
    }

    int size() {
        return bodyIds.length;
    }
}

