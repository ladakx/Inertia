package com.ladakx.inertia.nms.render;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.items.InertiaItemResolver;
import com.ladakx.inertia.items.ItemManager; // Added import
import com.ladakx.inertia.render.ItemModelResolver;
import com.ladakx.inertia.utils.MinecraftVersions;

import java.lang.reflect.Constructor;
import java.util.Locale;

public class RenderFactoryInit {

    private RenderFactoryInit() {}

    /**
     * Ми повинні передати ItemManager сюди, щоб створити резолвер.
     */
    public static RenderFactory get(ItemManager itemManager) {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);
        String path = "com.ladakx.inertia.nms." + version + ".render.RenderFactory";

        RenderFactory renderFactory = null;

        // Create resolver with injected manager
        ItemModelResolver itemResolver = new InertiaItemResolver(itemManager);

        try {
            Class<?> clazz = Class.forName(path);
            // NMS implementations must accept ItemModelResolver in constructor
            Constructor<?> constructor = clazz.getConstructor(ItemModelResolver.class);
            renderFactory = (RenderFactory) constructor.newInstance(itemResolver);

        } catch (Exception e) {
            InertiaLogger.error("Failed to initialize RenderFactory for version " + version, e);
        }

        return renderFactory;
    }
}