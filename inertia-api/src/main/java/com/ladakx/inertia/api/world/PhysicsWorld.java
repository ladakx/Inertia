package com.ladakx.inertia.api.world;

import com.ladakx.inertia.api.interaction.PhysicsInteraction;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

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
}

