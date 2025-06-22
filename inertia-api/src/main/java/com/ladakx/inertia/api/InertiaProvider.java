/* Original project path: inertia-api/src/main/java/com/ladakx/inertia/api/InertiaProvider.java */

package com.ladakx.inertia.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A provider class to get the global instance of the Inertia API.
 */
public final class InertiaProvider {

    private static Inertia instance = null;

    private InertiaProvider() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Gets the singleton instance of the Inertia API.
     *
     * @return The active Inertia API instance.
     * @throws IllegalStateException if the API has not been registered yet.
     */
    public static @NotNull Inertia get() {
        if (instance == null) {
            throw new IllegalStateException("Inertia API has not been registered yet! Is the Inertia plugin loaded and enabled?");
        }
        return instance;
    }

    /**
     * Internal method to register the Inertia API instance.
     * This should only be called by the Inertia plugin itself.
     * @param api The API instance to register.
     */
    @ApiStatus.Internal
    public static void register(@NotNull Inertia api) {
        if (instance != null) {
            // This can happen on /reload, it's not a critical error but worth noting.
            System.err.println("Inertia API is being registered, but an old instance was present. This may indicate a server reload.");
        }
        instance = api;
    }

    /**
     * Internal method to unregister the Inertia API instance.
     * This should only be called by the Inertia plugin itself.
     */
    @ApiStatus.Internal
    public static void unregister() {
        instance = null;
    }
}