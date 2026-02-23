package com.ladakx.inertia.api.world;

import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.interaction.PhysicsInteraction;
import com.ladakx.inertia.api.physics.PhysicsBodySpec;
import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public interface PhysicsWorld {
    @NotNull
    World getBukkitWorld();

    void setSimulationPaused(boolean paused);

    boolean isSimulationPaused();

    void setGravity(@NotNull Vector gravity);

    @NotNull
    Vector getGravity();

    @NotNull
    Collection<PhysicsBody> getBodies();

    @NotNull
    PhysicsInteraction getInteraction();

    default @NotNull ApiResult<PhysicsBody> createBodyResult(@NotNull PhysicsBodySpec spec) {
        Objects.requireNonNull(spec, "spec");
        return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "error-occurred");
    }

    @Deprecated(forRemoval = false)
    default @Nullable PhysicsBody createBody(@NotNull PhysicsBodySpec spec) {
        Objects.requireNonNull(spec, "spec");
        return createBodyResult(spec).getValue();
    }
}
