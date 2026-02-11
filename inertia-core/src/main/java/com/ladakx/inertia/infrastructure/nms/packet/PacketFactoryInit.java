package com.ladakx.inertia.infrastructure.nms.packet;

import com.ladakx.inertia.common.MinecraftVersions;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PacketFactoryInit {
    
    private PacketFactoryInit() {}

    public static PacketFactory get() {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);
        String path = "com.ladakx.inertia.nms." + version + ".packet.PacketFactoryImpl";
        
        try {
            Class<?> clazz = Class.forName(path);
            Constructor<?> constructor = clazz.getConstructor();
            return (PacketFactory) constructor.newInstance();
        } catch (Exception e) {
            InertiaLogger.error("Failed to initialize PacketFactory for version " + version, e);
            return null;
        }
    }
}