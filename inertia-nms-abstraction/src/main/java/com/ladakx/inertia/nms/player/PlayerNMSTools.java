package com.ladakx.inertia.nms.player;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * This interface is used to modify player's camera rotation and play sounds.
 */
public interface PlayerNMSTools {
    /**
     * Modifies player's camera rotation.
     * @param player Player to modify
     * @param yaw New yaw
     * @param pitch New pitch
     * @param absolute If true, the new rotation will be set to the given values. If false, the new rotation will be added to the current rotation.
     */
    void modifyCameraRotation(Player player, float yaw, float pitch, boolean absolute);

    /**
     * Plays a sound to the player.
     * @param player Player to play the sound to
     * @param sound Sound to play
     * @param category Sound category
     * @param volume Volume of the sound
     * @param pitch Pitch of the sound
     */
    void playSound(Player player, String sound, SoundCategory category, float volume, float pitch);
}
