package com.ladakx.inertia.api.jolt;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.world.PhysicsWorld;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Controlled access to native Jolt objects (via jolt-jni) for advanced integrations.
 * <p>
 * All tasks are executed on the physics thread for the target world.
 * Implementations must not expose long-lived native objects to callers.
 */
@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface JoltService {

    @NotNull <T> CompletableFuture<T> submitRead(@NotNull PhysicsWorld world, @NotNull JoltReadTask<T> task);

    @NotNull <T> CompletableFuture<T> submitWrite(@NotNull PhysicsWorld world, @NotNull JoltWriteTask<T> task);
}

