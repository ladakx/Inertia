package com.ladakx.inertia.nms.v1_21_r8;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PlayerTools implements com.ladakx.inertia.infrastructure.nms.player.PlayerTools {

    private final Set<Relative> RELATIVE_FLAGS = new HashSet<>(Arrays.asList(
            Relative.X,
            Relative.Y,
            Relative.Z,
            Relative.X_ROT,
            Relative.Y_ROT));

    private final Set<Relative> ABSOLUTE_FLAGS = new HashSet<>(Arrays.asList(
            Relative.X,
            Relative.Y,
            Relative.Z));

    public PlayerTools() {
        // Constructor
    }

    @Override
    public void modifyCameraRotation(Player player, float yaw, float pitch, boolean absolute) {
        PositionMoveRotation mvr = new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, yaw, pitch);
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundPlayerPositionPacket(0, mvr, absolute ? ABSOLUTE_FLAGS : RELATIVE_FLAGS));
    }

    @Override
    public void playSound(Player player, String sound, SoundCategory category, float volume, float pitch) {
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundSoundPacket(
                Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.parse(sound))),
                SoundSource.values()[category.ordinal()],
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                volume,
                pitch,
                0L
        ));
    }
}
