package com.ladakx.inertia.features.listeners;

import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.infrastructure.nms.network.NetworkManager;
import com.ladakx.inertia.rendering.NetworkEntityTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class NetworkListener implements Listener {
    private final NetworkManager networkManager;
    private final NetworkEntityTracker tracker;

    public NetworkListener(NetworkManager networkManager) {
        this.networkManager = networkManager;
        this.tracker = InertiaPlugin.getInstance().getNetworkEntityTracker();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        networkManager.inject(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        networkManager.uninject(event.getPlayer());
        if (tracker != null) {
            tracker.removePlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // При смене мира нужно мгновенно скрыть старые сущности,
        // чтобы они не "перелетели" визуально или не остались висеть.
        if (tracker != null) {
            tracker.removePlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return; // Обработается в onWorldChange
        }
        if (event.getFrom().distanceSquared(event.getTo()) > 64 * 64) {
            // Если телепорт далеко, лучше сбросить трекер, чтобы пересчитать видимость сразу
            // Это предотвратит визуальные глитчи интерполяции через всю карту
            if (tracker != null) {
                tracker.removePlayer(event.getPlayer());
            }
        }
    }
}