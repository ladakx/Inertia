package com.ladakx.inertia.core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Проста обгортка над логгером Bukkit для додавання
 * кастомних рівнів або префіксів у майбутньому.
 * (Без змін з твого коду)
 */
public class InertiaPluginLogger {

    private final Logger logger;

    public InertiaPluginLogger(Logger logger) {
        this.logger = logger;
    }

    public void info(String message) {
        logger.info(message);
    }

    public void warn(String message) {
        logger.warning(message);
    }

    public void warning(String message) {
        logger.warning(message);
    }

    public void severe(String message) {
        logger.severe(message);
    }

    public void severe(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }

    public void log(Level level, String message) {
        logger.log(level, message);
    }
}