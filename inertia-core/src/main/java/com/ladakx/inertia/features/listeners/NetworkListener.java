package com.ladakx.inertia.features.listeners;

import com.ladakx.inertia.infrastructure.nms.network.NetworkManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class NetworkListener implements Listener {

    private final NetworkManager networkManager;

    public NetworkListener(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        networkManager.inject(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        networkManager.uninject(event.getPlayer());
    }
}