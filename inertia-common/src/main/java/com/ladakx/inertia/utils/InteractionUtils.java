package com.ladakx.inertia.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

public final class InteractionUtils {

    private InteractionUtils() {}

    /**
     * Отримує цільову локацію взаємодії:
     * - Якщо клік по блоку: повертає точку взаємодії або центр грані.
     * - Якщо клік у повітря: виконує рейкаст або бере точку на відстані.
     */
    public static Location getTargetLocation(Player player, PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            return event.getInteractionPoint() != null
                    ? event.getInteractionPoint()
                    : event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
        } else {
            // Клік в повітря - точка перед гравцем
            return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3));
        }
    }
}