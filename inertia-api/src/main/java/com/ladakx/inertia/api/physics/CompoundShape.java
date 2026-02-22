package com.ladakx.inertia.api.physics;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record CompoundShape(@NotNull List<ShapeInstance> children) implements PhysicsShape {
    public CompoundShape {
        Objects.requireNonNull(children, "children");
        if (children.isEmpty()) throw new IllegalArgumentException("children cannot be empty");
        children = Collections.unmodifiableList(List.copyOf(children));
    }

    @Override
    public @NotNull Kind kind() {
        return Kind.COMPOUND;
    }
}

