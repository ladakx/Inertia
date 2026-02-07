package com.ladakx.inertia.infrastructure.nms.network;

import com.ladakx.inertia.common.MinecraftVersions;
import com.ladakx.inertia.common.logging.InertiaLogger;

import java.lang.reflect.Constructor;
import java.util.Locale;

public final class NetworkManagerInit {

    private NetworkManagerInit() {}

    public static NetworkManager get() {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);
        String path = "com.ladakx.inertia.nms." + version + ".network.NetworkManagerImpl";

        try {
            Class<?> clazz = Class.forName(path);
            Constructor<?> constructor = clazz.getConstructor();
            return (NetworkManager) constructor.newInstance();
        } catch (Exception e) {
            InertiaLogger.error("Failed to initialize NetworkManager for version: " + version, e);
            return new NetworkManager() {
                @Override public void inject(org.bukkit.entity.Player player) {}
                @Override public void uninject(org.bukkit.entity.Player player) {}
            };
        }
    }
}