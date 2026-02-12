package com.ladakx.inertia.api;

import com.ladakx.inertia.api.world.IPhysicsWorld;
import com.ladakx.inertia.api.rendering.RenderingService;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class InertiaAPI {
    private static InertiaAPI instance;

    public static InertiaAPI get() {
        if (instance == null) {
            throw new IllegalStateException("InertiaAPI is not initialized. Check if Inertia plugin is enabled.");
        }
        return instance;
    }

    public static void setImplementation(@NotNull InertiaAPI implementation) {
        if (instance != null) {
            throw new IllegalStateException("InertiaAPI implementation is already registered.");
        }
        instance = implementation;
    }

    @Nullable
    public abstract InertiaPhysicsBody createBody(@NotNull Location location, @NotNull String bodyId);

    public abstract boolean isWorldSimulated(@NotNull String worldName);

    @Nullable
    public abstract IPhysicsWorld getPhysicsWorld(@NotNull World world);

    @NotNull
    public abstract Collection<IPhysicsWorld> getAllPhysicsWorlds();

    @NotNull
    public abstract RenderingService rendering();
}
