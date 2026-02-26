package com.ladakx.inertia.api.jolt;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.body.PhysicsBody;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only access to Jolt objects for the current physics world.
 * <p>
 * This context is only valid within the scope of a {@link JoltReadTask}.
 * Do not store returned native objects outside of the task.
 */
@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
public interface JoltReadContext {

    @NotNull PhysicsSystem physicsSystem();

    @NotNull BodyInterface bodyInterface();

    /**
     * Returns the underlying Jolt {@link Body} for an Inertia {@link PhysicsBody}.
     * <p>
     * The returned object is a native-backed wrapper; do not retain it outside the task.
     *
     * @throws IllegalArgumentException if the body is not owned by the current Inertia runtime
     */
    @NotNull Body bodyOf(@NotNull PhysicsBody body);
}

