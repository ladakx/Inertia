package com.ladakx.inertia.infrastructure.nms.network;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface NetworkEntityInteractionListener {
    boolean onNetworkEntityInteraction(@NotNull Player player, int entityId, boolean attack);
}
