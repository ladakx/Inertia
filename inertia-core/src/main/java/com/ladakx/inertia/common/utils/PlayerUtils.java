package com.ladakx.inertia.common.utils;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;

public class PlayerUtils {
    private PlayerUtils() {
        // utility class
    }

    public static Location getTargetLocation(Player player) {
        double maxDistance = 5.0;
        RayTraceResult result = player.rayTraceBlocks(maxDistance, FluidCollisionMode.NEVER);

        if (result != null && result.getHitPosition() != null) {
            return result.getHitPosition().toLocation(player.getWorld());
        } else {
            return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(maxDistance));
        }
    }

    public static Location getTargetLocation(Player player, PlayerInteractEvent event) {
        if (event.getInteractionPoint() != null) {
            return event.getInteractionPoint();
        }

        return getTargetLocation(player);
    }
}
