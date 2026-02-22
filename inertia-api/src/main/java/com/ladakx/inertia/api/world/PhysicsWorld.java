package com.ladakx.inertia.api.world;

import com.ladakx.inertia.api.interaction.PhysicsInteraction;
import com.ladakx.inertia.api.physics.PhysicsBodySpec;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface PhysicsWorld {
    @NotNull
    World getBukkitWorld();

    void setSimulationPaused(boolean paused);

    boolean isSimulationPaused();

    void setGravity(@NotNull Vector gravity);

    @NotNull
    Vector getGravity();

    @NotNull
    Collection<InertiaPhysicsBody> getBodies();

    @NotNull
    PhysicsInteraction getInteraction();

    /**
     * Spawns a physics body with an API-provided shape.
     * <p>
     * Implementations may return {@code null} if the world is not simulated or inputs are invalid.
     */
    default @Nullable InertiaPhysicsBody createBody(@NotNull PhysicsBodySpec spec) {
        throw new UnsupportedOperationException("createBody(spec) is not supported by this implementation");
    }
}
