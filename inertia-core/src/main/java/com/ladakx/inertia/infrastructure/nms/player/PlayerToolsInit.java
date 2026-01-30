package com.ladakx.inertia.infrastructure.nms.player;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.MinecraftVersions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

/**
 * Utility class for getting the correct PlayerNMSTools implementation based on the server version.
 */
public class PlayerToolsInit {

    private PlayerToolsInit() {
        // Utility class
    }

    /**
     * Get the PlayerTools implementation for the server version.
     * @return The PlayerTools implementation for the server version
     */
    public static PlayerTools get() {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);
        String path = "com.ladakx.inertia.nms."+version+".PlayerTools";

        PlayerTools playerTools = null;

        try {
            Class<?> clazz = Class.forName(path);
            Constructor<?> constructor = clazz.getConstructor();
            playerTools = (PlayerTools) constructor.newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            InertiaLogger.error("The server version you are using is not supported.");
            e.printStackTrace();
        }

        return playerTools;
    }
}
