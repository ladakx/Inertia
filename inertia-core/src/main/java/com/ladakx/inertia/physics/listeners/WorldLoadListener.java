package com.ladakx.inertia.physics.listeners;

import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class WorldLoadListener implements Listener {

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public WorldLoadListener(PhysicsWorldRegistry physicsWorldRegistry) {
        this.physicsWorldRegistry = physicsWorldRegistry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        physicsWorldRegistry.createWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        physicsWorldRegistry.removeWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        PhysicsWorld space = physicsWorldRegistry.getWorld(event.getWorld());
        if (space != null) {
            space.onChunkLoad(event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        PhysicsWorld space = physicsWorldRegistry.getWorld(event.getWorld());
        if (space != null) {
            space.onChunkUnload(event.getChunk().getX(), event.getChunk().getZ());
        }
    }
}
