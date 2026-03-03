package com.ladakx.inertia.core.impl.rendering.transform;

import com.ladakx.inertia.api.rendering.transform.EntityTransformContext;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MutableEntityTransformContext implements EntityTransformContext {

    private String modelId;
    private String entityKey;
    private boolean hasParent;
    private boolean passenger;
    private boolean syncPosition;
    private boolean syncRotation;
    private final Vector3f basePosition = new Vector3f();
    private final Quaternionf baseRotation = new Quaternionf();
    private final Vector3f defaultLocalOffset = new Vector3f();
    private final Quaternionf defaultLocalRotation = new Quaternionf();
    private final Vector3f stackTranslation = new Vector3f();
    private final Quaternionf stackRotation = new Quaternionf();

    public void set(String modelId,
                    String entityKey,
                    boolean hasParent,
                    boolean passenger,
                    boolean syncPosition,
                    boolean syncRotation,
                    Vector3f basePosition,
                    Quaternionf baseRotation,
                    Vector3f defaultLocalOffset,
                    Quaternionf defaultLocalRotation,
                    Vector3f stackTranslation,
                    Quaternionf stackRotation) {
        this.modelId = modelId;
        this.entityKey = entityKey;
        this.hasParent = hasParent;
        this.passenger = passenger;
        this.syncPosition = syncPosition;
        this.syncRotation = syncRotation;
        this.basePosition.set(basePosition);
        this.baseRotation.set(baseRotation);
        this.defaultLocalOffset.set(defaultLocalOffset);
        this.defaultLocalRotation.set(defaultLocalRotation);
        this.stackTranslation.set(stackTranslation);
        this.stackRotation.set(stackRotation);
    }

    @Override
    public @NotNull String modelId() {
        return modelId;
    }

    @Override
    public @NotNull String entityKey() {
        return entityKey;
    }

    @Override
    public boolean hasParent() {
        return hasParent;
    }

    @Override
    public boolean passenger() {
        return passenger;
    }

    @Override
    public boolean syncPosition() {
        return syncPosition;
    }

    @Override
    public boolean syncRotation() {
        return syncRotation;
    }

    @Override
    public @NotNull Vector3f basePosition() {
        return basePosition;
    }

    @Override
    public @NotNull Quaternionf baseRotation() {
        return baseRotation;
    }

    @Override
    public @NotNull Vector3f defaultLocalOffset() {
        return defaultLocalOffset;
    }

    @Override
    public @NotNull Quaternionf defaultLocalRotation() {
        return defaultLocalRotation;
    }

    @Override
    public @NotNull Vector3f stackTranslation() {
        return stackTranslation;
    }

    @Override
    public @NotNull Quaternionf stackRotation() {
        return stackRotation;
    }
}
