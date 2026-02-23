package com.ladakx.inertia.api.capability;

import com.ladakx.inertia.api.physics.PhysicsShape;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class ApiCapabilities {

    private ApiCapabilities() {
    }

    public static @NotNull ApiCapability forShape(@NotNull PhysicsShape.Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case BOX -> ApiCapability.PHYSICS_SHAPE_BOX;
            case SPHERE -> ApiCapability.PHYSICS_SHAPE_SPHERE;
            case CAPSULE -> ApiCapability.PHYSICS_SHAPE_CAPSULE;
            case CYLINDER -> ApiCapability.PHYSICS_SHAPE_CYLINDER;
            case TAPERED_CAPSULE -> ApiCapability.PHYSICS_SHAPE_TAPERED_CAPSULE;
            case TAPERED_CYLINDER -> ApiCapability.PHYSICS_SHAPE_TAPERED_CYLINDER;
            case CONVEX_HULL -> ApiCapability.PHYSICS_SHAPE_CONVEX_HULL;
            case COMPOUND -> ApiCapability.PHYSICS_SHAPE_COMPOUND;
            case CUSTOM -> ApiCapability.PHYSICS_SHAPE_CUSTOM;
        };
    }
}
