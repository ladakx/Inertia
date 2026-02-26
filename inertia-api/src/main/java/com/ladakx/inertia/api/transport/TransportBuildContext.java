package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.InertiaApi;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.world.PhysicsWorld;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Build context passed to {@link TransportType#build(TransportBuildContext)}.
 * <p>
 * All methods are safe to call on the main thread. Implementations should not
 * assume a stable tick rate; use {@link TransportTickContext} for runtime updates.
 */
@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public interface TransportBuildContext {

    @NotNull InertiaApi api();

    /**
     * Plugin that registered the {@link TransportType}.
     */
    @NotNull Plugin typeOwner();

    /**
     * Plugin that owns the spawned transport instance.
     * <p>
     * Used for cleanup when the plugin disables.
     */
    @NotNull Plugin instanceOwner();

    @NotNull TransportSpawnRequest request();

    /**
     * Convenience: resolves the physics world for the request location.
     */
    default @Nullable PhysicsWorld physicsWorld() {
        Location location = request().location();
        if (location.getWorld() == null) return null;
        return api().getPhysicsWorld(location.getWorld());
    }

    /**
     * Convenience: requires a simulated physics world.
     *
     * @throws IllegalStateException if the target world is not simulated
     */
    default @NotNull PhysicsWorld requirePhysicsWorld() {
        PhysicsWorld world = physicsWorld();
        if (world == null) {
            Location location = request().location();
            String name = (location.getWorld() == null) ? "<null>" : Objects.requireNonNull(location.getWorld()).getName();
            throw new IllegalStateException("World is not simulated: " + name);
        }
        return world;
    }
}

