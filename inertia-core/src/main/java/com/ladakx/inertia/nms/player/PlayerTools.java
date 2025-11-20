package com.ladakx.inertia.nms.player;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.utils.MinecraftVersions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

/**
 * Utility class for getting the correct PlayerNMSTools implementation based on the server version.
 */
public class PlayerTools {

    /**
     * Get the PlayerNMSTools implementation for the server version.
     * @return The PlayerNMSTools implementation for the server version
     */
    public static PlayerNMSTools get() {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);
        String path = "com.ladakx.inertia.nms."+version+".PlayerTools";

        PlayerNMSTools playerTools = null;

        try {
            Class<?> clazz = Class.forName(path);
            Constructor<?> constructor = clazz.getConstructor();
            playerTools = (PlayerNMSTools) constructor.newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            InertiaLogger.error("The server version you are using is not supported.");
            e.printStackTrace();
        }

        return playerTools;
    }
}
