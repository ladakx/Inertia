package com.ladakx.inertia.nms.v1_21_r2.utils;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import java.util.List;

public final class PacketUtils {

    private PacketUtils() {}

    public static void send(Player player, Packet<?> packet) {
        if (player instanceof CraftPlayer craftPlayer) {
            ServerPlayer serverPlayer = craftPlayer.getHandle();
            serverPlayer.connection.send(packet);
        }
    }

    public static void send(Player player, List<Object> packets) {
        if (packets == null || packets.isEmpty()) return;
        if (player instanceof CraftPlayer craftPlayer) {
            ServerPlayer serverPlayer = craftPlayer.getHandle();
            for (Object packet : packets) {
                if (packet instanceof Packet<?> p) {
                    serverPlayer.connection.send(p);
                }
            }
        }
    }
}