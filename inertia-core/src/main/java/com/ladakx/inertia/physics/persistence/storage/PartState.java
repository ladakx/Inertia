package com.ladakx.inertia.physics.persistence.storage;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public record PartState(
        String partKey,
        double x, double y, double z,
        float rX, float rY, float rZ, float rW,
        double lvX, double lvY, double lvZ,
        double avX, double avY, double avZ,
        boolean anchored,
        double anchorX, double anchorY, double anchorZ
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public PartState {
        Objects.requireNonNull(partKey, "partKey");
    }
}