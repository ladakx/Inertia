package com.ladakx.inertia.jolt.listeners;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.jolt.space.SpaceManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Listener for loading and unloading worlds.
 */
public class WorldLoadListener implements Listener {

    private final SpaceManager spaceManager;

    public WorldLoadListener() {
        this.spaceManager = SpaceManager.getInstance();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        spaceManager.createSpace(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        spaceManager.removeSpace(event.getWorld());
    }
}
