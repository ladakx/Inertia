package com.ladakx.inertia.api.rendering.transform;

import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Mutable output container for world transform.
 */
public interface MutableEntityTransform {

    @NotNull Vector3f position();

    @NotNull Quaternionf rotation();
}

