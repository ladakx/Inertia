package com.ladakx.inertia.physics.factory;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.jetbrains.annotations.NotNull;

public final class ValidationUtils {

    private ValidationUtils() {}

    public enum ValidationResult {
        SUCCESS,
        OUT_OF_BOUNDS,
        OBSTRUCTED
    }

    public static ValidationResult canSpawnAt(@NotNull PhysicsWorld space, @NotNull ShapeRefC shapeRef, @NotNull RVec3 pos, @NotNull Quat rot) {
        ConstShape shape = shapeRef.getPtr();
        AaBox aabb = shape.getWorldSpaceBounds(RMat44.sRotationTranslation(rot, pos), Vec3.sReplicate(1.0f));

        if (!space.getBoundaryManager().isAABBInside(aabb)) {
            return ValidationResult.OUT_OF_BOUNDS;
        }

        if (space.checkOverlap(shape, pos, rot)) {
            return ValidationResult.OBSTRUCTED;
        }

        return ValidationResult.SUCCESS;
    }
}