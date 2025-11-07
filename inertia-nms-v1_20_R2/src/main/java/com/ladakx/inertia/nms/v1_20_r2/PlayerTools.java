package com.ladakx.inertia.nms.v1_20_r2;

import com.ladakx.inertia.nms.player.PlayerNMSTools;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.RelativeMovement;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PlayerTools implements PlayerNMSTools {

    private final Set<RelativeMovement> RELATIVE_FLAGS = new HashSet<>(Arrays.asList(
            RelativeMovement.X,
            RelativeMovement.Y,
            RelativeMovement.Z,
            RelativeMovement.X_ROT,
            RelativeMovement.Y_ROT));

    private final Set<RelativeMovement> ABSOLUTE_FLAGS = new HashSet<>(Arrays.asList(
            RelativeMovement.X,
            RelativeMovement.Y,
            RelativeMovement.Z));


    @Override
    public void modifyCameraRotation(Player player, float yaw, float pitch, boolean absolute) {
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundPlayerPositionPacket(0, 0, 0, yaw, pitch, absolute ? ABSOLUTE_FLAGS : RELATIVE_FLAGS, 0));
    }

    @Override
    public void playSound(Player player, String sound, SoundCategory category, float volume, float pitch) {
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundSoundPacket(
                Holder.direct(SoundEvent.createVariableRangeEvent(new ResourceLocation(sound))),
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
