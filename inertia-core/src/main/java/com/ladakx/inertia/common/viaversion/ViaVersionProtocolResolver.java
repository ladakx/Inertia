package com.ladakx.inertia.common.viaversion;

import com.ladakx.inertia.common.MinecraftVersions;
import com.viaversion.viaversion.api.Via;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ViaVersionProtocolResolver {

    private ViaVersionProtocolResolver() {
        throw new UnsupportedOperationException();
    }

    public static int getPlayerProtocol(@Nullable Player player) {
        if (player == null) return resolveServerProtocol();
        return getPlayerProtocol(player.getUniqueId());
    }

    public static int getPlayerProtocol(@Nullable UUID uuid) {
        if (uuid == null) return resolveServerProtocol();
        if (!isViaVersionInstalled()) return resolveServerProtocol();

        try {
            int protocol = Via.getAPI().getPlayerVersion(uuid);
            return protocol > 0 ? protocol : resolveServerProtocol();
        } catch (Throwable ignored) {
            return resolveServerProtocol();
        }
    }

    private static boolean isViaVersionInstalled() {
        return Bukkit.getPluginManager().getPlugin("ViaVersion") != null;
    }

    private static int resolveServerProtocol() {
        MinecraftVersions.Version current = MinecraftVersions.CURRENT;
        return current != null ? current.networkProtocol : Integer.MAX_VALUE;
    }
}
