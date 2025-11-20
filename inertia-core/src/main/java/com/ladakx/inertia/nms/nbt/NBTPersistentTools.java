package com.ladakx.inertia.nms.nbt;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.utils.MinecraftVersions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

/**
 * Utility class for getting the correct NBTPersistent implementation based on the server version.
 */
public class NBTPersistentTools {

    /**
     * Get the NBTPersistent implementation for the server version.
     * @return The NBTPersistent implementation for the server version
     */
    public static NBTPersistent get() {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);
        String path = "com.ladakx.inertia.nms."+version+".NBTPersistent";

        NBTPersistent nbtPersistentTools = null;

        try {
            Class<?> clazz = Class.forName(path);
            Constructor<?> constructor = clazz.getConstructor();
            nbtPersistentTools = (NBTPersistent) constructor.newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            InertiaLogger.error("The server version you are using is not supported.");
            e.printStackTrace();
        }

        return nbtPersistentTools;
    }
}
