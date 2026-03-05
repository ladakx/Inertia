package com.ladakx.inertia.api.transport;

public record AntiRollBarSpec(int leftWheelIndex,
                              int rightWheelIndex,
                              float stiffness) {
    public AntiRollBarSpec {
        if (leftWheelIndex < 0 || rightWheelIndex < 0) {
            throw new IllegalArgumentException("wheel indexes must be >= 0");
        }
        if (leftWheelIndex == rightWheelIndex) {
            throw new IllegalArgumentException("leftWheelIndex and rightWheelIndex must differ");
        }
        if (stiffness < 0f) {
            throw new IllegalArgumentException("stiffness must be >= 0");
        }
    }
}
