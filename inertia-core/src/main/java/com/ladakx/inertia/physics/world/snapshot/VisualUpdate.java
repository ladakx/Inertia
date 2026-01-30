package com.ladakx.inertia.physics.world.snapshot;

import com.ladakx.inertia.rendering.VisualEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Data Transfer Object (DTO) representing a single visual update instruction.
 * Calculated in the physics thread, applied in the main thread.
 */
public record VisualUpdate(
        VisualEntity visual,
        Vector3f position,
        Quaternionf rotation,
        Vector3f centerOffset,
        boolean rotateTranslation,
        boolean visible
) {
}