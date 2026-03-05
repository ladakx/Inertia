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

import java.util.Map;

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
     * Spawn a body and mark it as persistent for save/load operations.
     *
     * @param customData plugin-defined string metadata persisted with the body
     */
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    default @NotNull ApiResult<PhysicsBody> spawnPersistent(@NotNull Plugin owner,
                                                             @NotNull PhysicsWorld world,
                                                             @NotNull PhysicsBodySpec spec,
                                                             @Nullable Map<String, String> customData) {
        return spawn(owner, world, spec);
    }

    /**
     * Updates plugin-defined persistent metadata for a tracked body.
     */
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    default void setPersistentData(@NotNull Plugin owner,
                                   @NotNull PhysicsBody body,
                                   @Nullable Map<String, String> customData) {
    }

    /**
     * Returns plugin-defined persistent metadata for the body.
     */
    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    default @NotNull Map<String, String> getPersistentData(@NotNull PhysicsBody body) {
        return Map.of();
    }

    /**
     * Saves all persistent bodies owned by the plugin into owner data folder path.
     *
     * @param relativePath e.g. {@code "inertia/persistent-bodies.yml"}
     * @return number of saved bodies
     */
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    default @NotNull ApiResult<Integer> saveOwned(@NotNull Plugin owner, @NotNull String relativePath) {
        return ApiResult.failure(com.ladakx.inertia.api.ApiErrorCode.UNSUPPORTED_OPERATION, "error-occurred");
    }

    /**
     * Loads previously saved persistent bodies from owner data folder path.
     *
     * @param relativePath e.g. {@code "inertia/persistent-bodies.yml"}
     * @return number of spawned bodies
     */
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    default @NotNull ApiResult<Integer> loadOwned(@NotNull Plugin owner, @NotNull String relativePath) {
        return ApiResult.failure(com.ladakx.inertia.api.ApiErrorCode.UNSUPPORTED_OPERATION, "error-occurred");
    }

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
