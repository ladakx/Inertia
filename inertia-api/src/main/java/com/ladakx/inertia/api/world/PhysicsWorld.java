package com.ladakx.inertia.api.world;

import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.interaction.PhysicsInteraction;
import com.ladakx.inertia.api.physics.PhysicsBodySpec;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface PhysicsWorld {
    @NotNull
    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    World getBukkitWorld();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void setSimulationPaused(boolean paused);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    boolean isSimulationPaused();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void setGravity(@NotNull Vector gravity);

    @NotNull
    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    Vector getGravity();

    @NotNull
    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    Collection<PhysicsBody> getBodies();

    @NotNull
    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    PhysicsInteraction getInteraction();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    default @NotNull ApiResult<PhysicsBody> createBodyResult(@NotNull PhysicsBodySpec spec) {
        Objects.requireNonNull(spec, "spec");
        return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "error-occurred");
    }

    @Deprecated(forRemoval = false)
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    default @Nullable PhysicsBody createBody(@NotNull PhysicsBodySpec spec) {
        Objects.requireNonNull(spec, "spec");
        return createBodyResult(spec).getValue();
    }
}
