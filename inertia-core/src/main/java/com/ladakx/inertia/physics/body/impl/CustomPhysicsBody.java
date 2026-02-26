package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.ladakx.inertia.api.body.PhysicsBodyType;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.events.PhysicsEventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CustomPhysicsBody extends AbstractPhysicsBody {

    private final String bodyId;

    public CustomPhysicsBody(@NotNull PhysicsWorld space,
                             @NotNull BodyCreationSettings bodySettings,
                             @Nullable String bodyId,
                             @NotNull PhysicsEventDispatcher eventDispatcher) {
        super(space, Objects.requireNonNull(bodySettings, "bodySettings"), Objects.requireNonNull(eventDispatcher, "eventDispatcher"));
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
