package com.ladakx.inertia.api;

/**
 * CompatibilityAPI is used to detect the runtime environment.
 * In this case, it checks if the server is running Paper, a high-performance fork of Spigot.
 */
public final class CompatibilityAPI {

    // A static boolean flag indicating if the server is running Paper.
    private static final boolean isPaper;

    // Static initializer block runs once when the class is loaded.
    // It attempts to load a Paper-specific class. If successful, the flag is set to true.
    static {
        boolean isPaper1;
        try {
            // Try to load a class that only exists on Paper servers.
            Class.forName("com.destroystokyo.paper.VersionHistoryManager$VersionData");
            isPaper1 = true;
        } catch (ClassNotFoundException ex) {
            // If the class is not found, then the server is not running Paper.
            isPaper1 = false;
        }
        // Set the isPaper flag based on the result.
        isPaper = isPaper1;
    }

    /**
     * Returns whether the current server is running Paper.
     *
     * @return true if running on a Paper server, false otherwise.
     */
    public static boolean isPaper() {
        return isPaper;
    }
}