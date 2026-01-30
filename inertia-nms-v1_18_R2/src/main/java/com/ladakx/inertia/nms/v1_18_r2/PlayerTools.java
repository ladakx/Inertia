package com.ladakx.inertia.nms.v1_18_r2;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PlayerTools implements com.ladakx.inertia.infrastructure.nms.player.PlayerTools {

    private final Set<ClientboundPlayerPositionPacket.RelativeArgument> RELATIVE_FLAGS = new HashSet<>(Arrays.asList(
            ClientboundPlayerPositionPacket.RelativeArgument.X,
            ClientboundPlayerPositionPacket.RelativeArgument.Y,
            ClientboundPlayerPositionPacket.RelativeArgument.Z,
            ClientboundPlayerPositionPacket.RelativeArgument.X_ROT,
            ClientboundPlayerPositionPacket.RelativeArgument.Y_ROT));

    private final Set<ClientboundPlayerPositionPacket.RelativeArgument> ABSOLUTE_FLAGS = new HashSet<>(Arrays.asList(
            ClientboundPlayerPositionPacket.RelativeArgument.X,
            ClientboundPlayerPositionPacket.RelativeArgument.Y,
            ClientboundPlayerPositionPacket.RelativeArgument.Z));

    public PlayerTools() {
        // Constructor
    }

    @Override
    public void modifyCameraRotation(Player player, float yaw, float pitch, boolean absolute) {
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundPlayerPositionPacket(
                0,
                0,
                0,
                yaw,
                pitch,
                absolute ? ABSOLUTE_FLAGS : RELATIVE_FLAGS,
                0, false));
    }

    @Override
    public void playSound(Player player, String sound, SoundCategory category, float volume, float pitch) {
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundSoundPacket(
                new SoundEvent(new ResourceLocation(sound)),
                SoundSource.values()[category.ordinal()],
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                volume,
                pitch
        ));
    }
}
