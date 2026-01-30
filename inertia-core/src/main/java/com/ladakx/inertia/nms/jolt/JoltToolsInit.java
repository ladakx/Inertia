package com.ladakx.inertia.nms.jolt;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.utils.MinecraftVersions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

/**
 * BulletTools class is used to get the JoltNMSTools instance.
 */
public class JoltToolsInit {

    private JoltToolsInit() {
        // Utility class
    }

    /**
     * Get the JoltTools instance.
     * @return JoltTools instance.
     */
    public static JoltTools get() {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);
        String path = "com.ladakx.inertia.nms."+version+".JoltTools";

        JoltTools bulletTools = null;

        try {
            Class<?> clazz = Class.forName(path);
            Constructor<?> constructor = clazz.getConstructor();
            return (JoltTools) constructor.newInstance();
        } catch (Exception e) {
            InertiaLogger.error("Failed to initialize JoltTools for NMS version: " + version, e);
            return null; // Or throw a RuntimeException if critical
        }
    }
}
