package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CustomPhysicsBody extends AbstractPhysicsBody {

    private final String bodyId;

    public CustomPhysicsBody(@NotNull PhysicsWorld space,
                             @NotNull BodyCreationSettings bodySettings,
                             @Nullable String bodyId) {
        super(space, Objects.requireNonNull(bodySettings, "bodySettings"));
        this.bodyId = (bodyId != null && !bodyId.isBlank())
                ? bodyId
                : ("custom:" + getUuid());
    }

    @Override
    public @NotNull String getBodyId() {
        return bodyId;
    }

    @Override
    public @NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.CUSTOM;
    }
}

