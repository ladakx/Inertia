package com.ladakx.inertia.physics.world.fluid;

import java.util.Objects;

record FluidContactSnapshot(int[] bodyIds,
                            byte[] mediumIds,
                            float[] surfaceHeights,
                            float[] areaFractions,
                            float[] centerXs,
                            float[] centerZs,
                            float[] flowXs,
                            float[] flowYs,
                            float[] flowZs) {
    static final FluidContactSnapshot EMPTY = new FluidContactSnapshot(
            new int[0], new byte[0], new float[0], new float[0], new float[0], new float[0], new float[0], new float[0], new float[0]
    );

    FluidContactSnapshot(int[] bodyIds,
                         byte[] mediumIds,
                         float[] surfaceHeights,
                         float[] areaFractions,
                         float[] centerXs,
                         float[] centerZs,
                         float[] flowXs,
                         float[] flowYs,
                         float[] flowZs) {
        this.bodyIds = Objects.requireNonNull(bodyIds, "bodyIds");
        this.mediumIds = Objects.requireNonNull(mediumIds, "mediumIds");
        this.surfaceHeights = Objects.requireNonNull(surfaceHeights, "surfaceHeights");
        this.areaFractions = Objects.requireNonNull(areaFractions, "areaFractions");
        this.centerXs = Objects.requireNonNull(centerXs, "centerXs");
        this.centerZs = Objects.requireNonNull(centerZs, "centerZs");
        this.flowXs = Objects.requireNonNull(flowXs, "flowXs");
        this.flowYs = Objects.requireNonNull(flowYs, "flowYs");
        this.flowZs = Objects.requireNonNull(flowZs, "flowZs");
        if (bodyIds.length != mediumIds.length
                || bodyIds.length != surfaceHeights.length
                || bodyIds.length != areaFractions.length
                || bodyIds.length != centerXs.length
                || bodyIds.length != centerZs.length
                || bodyIds.length != flowXs.length
                || bodyIds.length != flowYs.length
                || bodyIds.length != flowZs.length) {
            throw new IllegalArgumentException("Snapshot arrays must have identical lengths");
        }
    }

    int size() {
        return bodyIds.length;
    }
}
