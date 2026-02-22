package com.ladakx.inertia.common.viaversion;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection-only ViaVersion bridge to avoid hard runtime dependency.
 */
public final class ViaVersionProtocolResolver {

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile Method viaGetApi;
    private static volatile Method apiGetPlayerVersion;

    private ViaVersionProtocolResolver() {
        throw new UnsupportedOperationException();
    }

    public static int getPlayerProtocol(@Nullable Player player) {
        if (player == null) return -1;
        return getPlayerProtocol(player.getUniqueId());
    }

    public static int getPlayerProtocol(@Nullable UUID uuid) {
        if (uuid == null) return -1;
        ensureInitialized();
        if (!available) return -1;
        try {
            Object api = viaGetApi.invoke(null);
            Object protocol = apiGetPlayerVersion.invoke(api, uuid);
            if (protocol instanceof Integer i) return i;
            if (protocol instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static void ensureInitialized() {
        if (initialized) return;
        synchronized (ViaVersionProtocolResolver.class) {
            if (initialized) return;
            try {
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
                viaGetApi = viaClass.getMethod("getAPI");
                Object api = viaGetApi.invoke(null);
                apiGetPlayerVersion = api.getClass().getMethod("getPlayerVersion", UUID.class);
                available = true;
            } catch (Throwable ignored) {
                available = false;
            } finally {
                initialized = true;
            }
        }
    }
}

