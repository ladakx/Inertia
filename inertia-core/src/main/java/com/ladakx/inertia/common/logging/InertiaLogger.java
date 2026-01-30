package com.ladakx.inertia.common.logging;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for handling plugin logging.
 */
public class InertiaLogger {

    private static Logger logger;
    private static final String DEBUG_PREFIX = "[Inertia-Debug] ";

    // Зберігаємо стан дебагу локально, замість запиту до ConfigManager
    private static volatile boolean debugMode = false;

    private InertiaLogger() {}

    public static void init(JavaPlugin plugin) {
        logger = plugin.getLogger();
    }

    /**
     * Оновлює стан режиму налагодження.
     * Викликається з ConfigManager після завантаження налаштувань.
     */
    public static void setDebugMode(boolean active) {
        debugMode = active;
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
        if (debugMode) {
            logger.warning(DEBUG_PREFIX + message);
        }
    }

    private static void ensureInit() {
        if (logger == null) {
            throw new IllegalStateException("InertiaLogger has not been initialized! Call init() in onEnable.");
        }
    }
}