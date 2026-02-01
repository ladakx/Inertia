package com.ladakx.inertia.physics.listeners;

import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class WorldLoadListener implements Listener {

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public WorldLoadListener(PhysicsWorldRegistry physicsWorldRegistry) {
        this.physicsWorldRegistry = physicsWorldRegistry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        physicsWorldRegistry.createSpace(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        physicsWorldRegistry.removeSpace(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        // Получаем физический мир
        PhysicsWorld space = physicsWorldRegistry.getSpace(event.getWorld());
        if (space != null) {
            // Уведомляем мир о загрузке чанка для восстановления визуалов
            space.onChunkLoad(event.getChunk().getX(), event.getChunk().getZ());
        }
    }
}