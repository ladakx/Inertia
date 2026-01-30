package com.ladakx.inertia.infrastructure.nms.render;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.features.items.InertiaItemResolver;
import com.ladakx.inertia.features.items.ItemRegistry; // Added import
import com.ladakx.inertia.rendering.ItemModelResolver;
import com.ladakx.inertia.common.MinecraftVersions;
import com.ladakx.inertia.rendering.RenderFactory;

import java.lang.reflect.Constructor;
import java.util.Locale;

public class RenderFactoryInit {

    private RenderFactoryInit() {}

    /**
     * Ми повинні передати ItemRegistry сюди, щоб створити резолвер.
     */
    public static RenderFactory get(ItemRegistry itemRegistry) {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);
        String path = "com.ladakx.inertia.nms." + version + ".render.RenderFactory";

        RenderFactory renderFactory = null;

        // Create resolver with injected manager
        ItemModelResolver itemResolver = new InertiaItemResolver(itemRegistry);

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