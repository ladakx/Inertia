package com.ladakx.inertia.core.impl.rendering.transform;

import com.ladakx.inertia.api.rendering.transform.MutableEntityTransform;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MutableEntityTransformImpl implements MutableEntityTransform {

    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();

    @Override
    public @NotNull Vector3f position() {
        return position;
    }

    @Override
    public @NotNull Quaternionf rotation() {
        return rotation;
    }
}
