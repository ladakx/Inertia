package com.ladakx.inertia.core;

import java.util.logging.Logger;

/**
 * A simple static logger wrapper to avoid passing the plugin logger instance everywhere.
 */
public final class InertiaPluginLogger {

    private static Logger logger;

    private InertiaPluginLogger() {}

    /**
     * Initializes the logger. Must be called once from the main plugin class.
     * @param pluginLogger The logger instance from the JavaPlugin.
     */
    public static void initialize(Logger pluginLogger) {
        if (logger == null) {
            logger = pluginLogger;
        }
    }

    public static void info(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }

    public static void warning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    public static void severe(String message) {
        if (logger != null) {
            logger.severe(message);
        }
    }
}
