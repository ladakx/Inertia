package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Extensible shape descriptor for future/engine-specific shapes.
 * <p>
 * Current engine versions may reject unknown custom shapes.
 */
public record CustomShape(@NotNull String type, @NotNull Map<String, Object> params) implements PhysicsShape {
    public CustomShape {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(params, "params");
        if (type.isBlank()) throw new IllegalArgumentException("type cannot be blank");
        params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    @Override
    public @NotNull Kind kind() {
        return Kind.CUSTOM;
    }
}

