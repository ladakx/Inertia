package com.ladakx.inertia.api.rendering.entity;

import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * One local transform component used by {@link TransformStack}.
 * <p>
 * Semantics:
 * <ul>
 *     <li>{@code translation} is applied in the current local frame (it is rotated by the accumulated rotation).</li>
 *     <li>{@code rotation} is then multiplied onto the accumulated rotation.</li>
 * </ul>
 */
public record TransformComponent(@NotNull Vector3f translation, @NotNull Quaternionf rotation) {
    public TransformComponent {
        Objects.requireNonNull(translation, "translation");
        Objects.requireNonNull(rotation, "rotation");
    }

    public static @NotNull TransformComponent identity() {
        return new TransformComponent(new Vector3f(), new Quaternionf());
    }
}

