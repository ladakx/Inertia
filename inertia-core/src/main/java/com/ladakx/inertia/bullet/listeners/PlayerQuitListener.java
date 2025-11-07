package com.ladakx.inertia.bullet.listeners;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.bullet.space.SpaceManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PlayerQuitListener listens for when a player quits the server.
 * Upon a player quitting, this listener will notify the SpaceManager to disable the debug for that player.
 */
public class PlayerQuitListener implements Listener {

    // Instance of SpaceManager which handles various physics spaces.
    private final SpaceManager spaceManager;

    /**
     * Initializes the PlayerQuitListener and retrieves the SpaceManager instance from RoseCore.
     */
    public PlayerQuitListener() {
        this.spaceManager = InertiaPlugin.getBulletManager().getSpaceManager();
    }

    /**
     * Called when a player quits the server.
     * This method disables any debug information for the quitting player in the physics space.
     * The event is handled at MONITOR priority and is ignored if cancelled.
     *
     * @param event the PlayerQuitEvent triggered when a player exits the server.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        spaceManager.disableDebugBar(event.getPlayer());
    }
}