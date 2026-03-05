package com.ladakx.inertia.api.transport;

public record DifferentialSpec(int leftWheelIndex,
                               int rightWheelIndex,
                               float leftWheelTorqueRatio,
                               float rightWheelTorqueRatio,
                               float limitedSlipRatio,
                               float engineTorqueRatio) {
    public DifferentialSpec {
        if (leftWheelIndex < 0 || rightWheelIndex < 0) {
            throw new IllegalArgumentException("wheel indexes must be >= 0");
        }
        if (leftWheelIndex == rightWheelIndex) {
            throw new IllegalArgumentException("leftWheelIndex and rightWheelIndex must differ");
        }
        if (leftWheelTorqueRatio < 0f || rightWheelTorqueRatio < 0f) {
            throw new IllegalArgumentException("wheel torque ratios must be >= 0");
        }
        if (limitedSlipRatio <= 0f) {
            throw new IllegalArgumentException("limitedSlipRatio must be > 0");
        }
        if (engineTorqueRatio < 0f) {
            throw new IllegalArgumentException("engineTorqueRatio must be >= 0");
        }
    }
}
