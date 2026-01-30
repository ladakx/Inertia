package com.ladakx.inertia.nms.v1_16_r3;

import net.minecraft.server.v1_16_R3.*;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PlayerTools implements com.ladakx.inertia.infrastructure.nms.player.PlayerTools {

    private final Set<PacketPlayOutPosition.EnumPlayerTeleportFlags> RELATIVE_FLAGS = new HashSet<>(Arrays.asList(PacketPlayOutPosition.EnumPlayerTeleportFlags.X,
            PacketPlayOutPosition.EnumPlayerTeleportFlags.Y,
            PacketPlayOutPosition.EnumPlayerTeleportFlags.Z,
            PacketPlayOutPosition.EnumPlayerTeleportFlags.X_ROT,
            PacketPlayOutPosition.EnumPlayerTeleportFlags.Y_ROT));

    private final Set<PacketPlayOutPosition.EnumPlayerTeleportFlags> ABSOLUTE_FLAGS = new HashSet<>(Arrays.asList(PacketPlayOutPosition.EnumPlayerTeleportFlags.X,
            PacketPlayOutPosition.EnumPlayerTeleportFlags.Y,
            PacketPlayOutPosition.EnumPlayerTeleportFlags.Z));

    public PlayerTools() {
        // Constructor
    }

    @Override
    public void modifyCameraRotation(Player player, float yaw, float pitch, boolean absolute) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutPosition(
                0,
                0,
                0,
                yaw,
                pitch,
                absolute ? ABSOLUTE_FLAGS : RELATIVE_FLAGS,
                0));
    }

    @Override
    public void playSound(Player player, String sound, SoundCategory category, float volume, float pitch) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutNamedSoundEffect(
                new SoundEffect(new MinecraftKey(sound)),
                net.minecraft.server.v1_16_R3.SoundCategory.values()[category.ordinal()],
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                volume,
                pitch
        ));
    }
}
