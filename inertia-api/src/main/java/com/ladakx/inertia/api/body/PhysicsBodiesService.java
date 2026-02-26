package com.ladakx.inertia.api.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.physics.PhysicsBodySpec;
import com.ladakx.inertia.api.world.PhysicsWorld;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stable facade for creating and tracking physics bodies owned by external plugins.
 * <p>
 * The goal is to keep Inertia as a "bridge" that:
 * <ul>
 *     <li>creates bodies and registers them in Inertia world registries</li>
 *     <li>fires lifecycle events (pre/post spawn, pre/post destroy)</li>
 *     <li>tracks ownership for safe cleanup on plugin disable</li>
 * </ul>
 */
@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface PhysicsBodiesService {

    /**
     * Spawn a body using Inertia's high-level {@link PhysicsBodySpec}.
     */
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    @NotNull ApiResult<PhysicsBody> spawn(@NotNull Plugin owner, @NotNull PhysicsWorld world, @NotNull PhysicsBodySpec spec);

    /**
     * Spawn a body using raw Jolt settings (direct access).
     * <p>
     * This gives maximum flexibility for custom shapes and advanced engine features.
     * The created body is still tracked by Inertia and participates in lifecycle events.
     */
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    @NotNull ApiResult<PhysicsBody> spawnJolt(@NotNull Plugin owner,
                                              @NotNull PhysicsWorld world,
                                              @NotNull BodyCreationSettings settings,
                                              @Nullable String bodyId);

    /**
     * Ownership metadata for the provided body.
     */
    @NotNull PhysicsBodyOwner ownerOf(@NotNull PhysicsBody body);

    /**
     * Best-effort cleanup: destroy all bodies owned by this plugin across all simulated worlds.
     */
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void destroyAll(@NotNull Plugin owner);
}

