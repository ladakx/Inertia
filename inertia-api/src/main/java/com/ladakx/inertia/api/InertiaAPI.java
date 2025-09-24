package com.ladakx.inertia.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * The main entry point for the Inertia API.
 * This class provides access to all core functionalities of the Inertia plugin.
 */
public abstract class InertiaAPI {

    private static InertiaAPI instance;


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
