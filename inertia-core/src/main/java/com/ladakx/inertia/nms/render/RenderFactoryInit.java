package com.ladakx.inertia.nms.render;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.items.InertiaItemResolver;
import com.ladakx.inertia.render.ItemModelResolver;
import com.ladakx.inertia.utils.MinecraftVersions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

public class RenderFactoryInit {

    private RenderFactoryInit() {
        // Utility class
    }

    /**
     * Get the RenderFactory implementation for the server version.
     * @return The RenderFactory implementation for the server version
     */
    public static RenderFactory get() {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);

        String path = "com.ladakx.inertia.nms." + version + ".render.RenderFactory";

        RenderFactory renderFactory = null;
        ItemModelResolver itemResolver = new InertiaItemResolver();

        try {
            Class<?> clazz = Class.forName(path);
            Constructor<?> constructor = clazz.getConstructor(ItemModelResolver.class);

            renderFactory = (RenderFactory) constructor.newInstance(itemResolver);

        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException | InstantiationException e) {
            InertiaLogger.error("The server version you are using (" + version + ") is not supported.");
            e.printStackTrace();
        }

        return renderFactory;
    }
}
