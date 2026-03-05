package com.ladakx.inertia.api.transport;

public record TrackedInput(float forward,
                           float leftRatio,
                           float rightRatio,
                           float brake) {
    public TrackedInput {
        if (forward < -1f || forward > 1f) {
            throw new IllegalArgumentException("forward must be in [-1..1]");
        }
        if (leftRatio < -1f || leftRatio > 1f) {
            throw new IllegalArgumentException("leftRatio must be in [-1..1]");
        }
        if (rightRatio < -1f || rightRatio > 1f) {
            throw new IllegalArgumentException("rightRatio must be in [-1..1]");
        }
        if (brake < 0f || brake > 1f) {
            throw new IllegalArgumentException("brake must be in [0..1]");
        }
    }

    public static TrackedInput idle() {
        return new TrackedInput(0f, 0f, 0f, 0f);
    }
}
