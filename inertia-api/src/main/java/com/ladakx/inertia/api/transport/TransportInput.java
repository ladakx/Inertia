package com.ladakx.inertia.api.transport;

public record TransportInput(float forward,
                             float right,
                             float brake,
                             float handBrake,
                             float clutch,
                             Integer manualGear) {

    public TransportInput {
        if (forward < -1f || forward > 1f) {
            throw new IllegalArgumentException("forward must be in [-1..1]");
        }
        if (right < -1f || right > 1f) {
            throw new IllegalArgumentException("right must be in [-1..1]");
        }
        if (brake < 0f || brake > 1f) {
            throw new IllegalArgumentException("brake must be in [0..1]");
        }
        if (handBrake < 0f || handBrake > 1f) {
            throw new IllegalArgumentException("handBrake must be in [0..1]");
        }
        if (clutch < 0f || clutch > 1f) {
            throw new IllegalArgumentException("clutch must be in [0..1]");
        }
    }

    public static TransportInput idle() {
        return new TransportInput(0f, 0f, 0f, 0f, 0f, null);
    }
}
