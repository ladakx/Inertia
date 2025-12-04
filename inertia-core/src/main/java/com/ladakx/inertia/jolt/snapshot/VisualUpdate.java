package com.ladakx.inertia.jolt.snapshot;

import com.ladakx.inertia.nms.render.runtime.VisualObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Data Transfer Object (DTO) representing a single visual update instruction.
 * Calculated in the physics thread, applied in the main thread.
 */
public record VisualUpdate(
        VisualObject visual,
        Vector3f position,
        Quaternionf rotation,
        Vector3f centerOffset,
        boolean rotateTranslation,
        boolean visible
) {
}