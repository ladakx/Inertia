package com.ladakx.inertia;

import com.ladakx.inertia.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for handling plugin logging.
 */
public class InertiaLogger {

    private static Logger logger;
    private static final String DEBUG_PREFIX = "[Inertia-Debug] ";

    private InertiaLogger() {}

    /**
     * Initialize the logger with the plugin instance.
     * Call this in onEnable.
     * @param plugin Your JavaPlugin instance
     */
    public static void init(JavaPlugin plugin) {
        logger = plugin.getLogger();
    }

    public static void info(Throwable throwable) {
        ensureInit();
        logger.log(Level.INFO, "An unexpected error occurred:", throwable);
    }

    public static void info(String message) {
        ensureInit();
        logger.info(message);
    }

    public static void info(String message, Throwable throwable) {
        ensureInit();
        logger.log(Level.INFO, message, throwable);
    }

    public static void warn(Throwable throwable) {
        ensureInit();
        logger.log(Level.WARNING, "An unexpected error occurred:", throwable);
    }

    public static void warn(String message) {
        ensureInit();
        logger.warning(message);
    }

    public static void warn(String message, Throwable throwable) {
        ensureInit();
        logger.log(Level.WARNING, message, throwable);
    }

    public static void error(String message) {
        ensureInit();
        logger.severe(message);
    }

    public static void error(String message, Throwable throwable) {
        ensureInit();
        logger.log(Level.SEVERE, message, throwable);
    }

    public static void error(Throwable throwable) {
        ensureInit();
        logger.log(Level.SEVERE, "An unexpected error occurred:", throwable);
    }

    public static void debug(String message) {
        ensureInit();
        if (ConfigManager.getInstance() != null) {
            boolean debug = ConfigManager.getInstance().getInertiaConfig().GENERAL.DEBUG.consoleDebug;
            if (debug) {
                logger.warning(DEBUG_PREFIX + message);
            }
        }
    }

    private static void ensureInit() {
        if (logger == null) {
            throw new IllegalStateException("InertiaLogger has not been initialized! Call init() in onEnable.");
        }
    }
}