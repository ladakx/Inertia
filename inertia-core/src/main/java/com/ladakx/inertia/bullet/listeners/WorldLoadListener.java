package com.ladakx.inertia.bullet.listeners;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.bullet.space.SpaceManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Listener for loading and unloading worlds.
 */
public class WorldLoadListener implements Listener {

    private final InertiaPlugin instance;
    private final SpaceManager spaceManager;

    public WorldLoadListener() {
        this.instance = InertiaPlugin.getInstance();
        this.spaceManager = InertiaPlugin.getBulletManager().getSpaceManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        spaceManager.load(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        spaceManager.unload(event.getWorld());
    }
}
