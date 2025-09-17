package com.ladakx.inertia.api;

import com.ladakx.inertia.api.body.BodyFactory;
import com.ladakx.inertia.api.world.PhysicsWorld;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The main entry point for the Inertia API.
 * This class provides access to all core functionalities of the Inertia plugin.
 */
public abstract class InertiaAPI {

    private static InertiaAPI instance;
    private final PhysicsWorld physicsWorld;

    /**
     * Internal constructor for the API.
     * Should only be called by the Inertia plugin itself.
     *
     * @param physicsWorld The implementation of the physics world.
     */
    @ApiStatus.Internal
    public InertiaAPI(PhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
    }

    /**
     * Gets the singleton instance of the Inertia API.
     *
     * @return The InertiaAPI instance.
     * @throws IllegalStateException if the API has not been initialized yet.
     */
    public static InertiaAPI get() {
        if (instance == null) {
            throw new IllegalStateException("InertiaAPI has not been initialized yet! Please ensure the Inertia plugin is loaded and enabled.");
        }
        return instance;
    }

    /**
     * Gets the manager for the physics world.
     * This provides access to all physical bodies and global physics settings.
     *
     * @return The PhysicsWorld manager.
     */
    @NotNull
    public PhysicsWorld getPhysicsWorld() {
        return physicsWorld;
    }

    /**
     * Gets the factory for creating new physical bodies.
     *
     * @return The BodyFactory instance.
     */
    @NotNull
    public abstract BodyFactory getBodyFactory();

    /**
     * Initializes the API instance. This method should only be called by the Inertia plugin.
     *
     * @param api The API instance to set.
     */
    @ApiStatus.Internal
    public static void setInstance(InertiaAPI api) {
        if (instance != null) {
            // This could be logged as a warning if re-initialization is not expected.
            return;
        }
        instance = api;
    }
}
