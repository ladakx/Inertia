package com.ladakx.inertia.api.rendering.transform;

import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Read-only input for entity transform computation.
 */
public interface EntityTransformContext {

    @NotNull String modelId();

    @NotNull String entityKey();

    boolean hasParent();

    boolean passenger();

    boolean syncPosition();

    boolean syncRotation();

    @NotNull Vector3f basePosition();

    @NotNull Quaternionf baseRotation();

    @NotNull Vector3f defaultLocalOffset();

    @NotNull Quaternionf defaultLocalRotation();

    @NotNull Vector3f stackTranslation();

    @NotNull Quaternionf stackRotation();
}

