package com.ladakx.inertia.api.transport;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record TransmissionSpec(@NotNull TransmissionMode mode,
                               float clutchStrength,
                               float switchLatency,
                               float switchTime,
                               float shiftUpRpm,
                               float shiftDownRpm,
                               @NotNull List<Float> forwardRatios,
                               @NotNull List<Float> reverseRatios) {

    public TransmissionSpec {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(forwardRatios, "forwardRatios");
        Objects.requireNonNull(reverseRatios, "reverseRatios");
        if (clutchStrength <= 0f) throw new IllegalArgumentException("clutchStrength must be > 0");
        if (switchLatency < 0f) throw new IllegalArgumentException("switchLatency must be >= 0");
        if (switchTime < 0f) throw new IllegalArgumentException("switchTime must be >= 0");
        if (shiftUpRpm <= 0f) throw new IllegalArgumentException("shiftUpRpm must be > 0");
        if (shiftDownRpm < 0f || shiftDownRpm >= shiftUpRpm) {
            throw new IllegalArgumentException("shiftDownRpm must be >= 0 and < shiftUpRpm");
        }
        if (forwardRatios.isEmpty()) throw new IllegalArgumentException("forwardRatios cannot be empty");
        if (reverseRatios.isEmpty()) throw new IllegalArgumentException("reverseRatios cannot be empty");
        forwardRatios = Collections.unmodifiableList(List.copyOf(forwardRatios));
        reverseRatios = Collections.unmodifiableList(List.copyOf(reverseRatios));
    }
}
