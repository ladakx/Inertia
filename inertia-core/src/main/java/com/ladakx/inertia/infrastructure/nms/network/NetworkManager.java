package com.ladakx.inertia.infrastructure.nms.network;

import org.bukkit.entity.Player;

public interface NetworkManager {
    void inject(Player player);
    void uninject(Player player);
}