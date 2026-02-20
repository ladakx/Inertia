package com.ladakx.inertia.physics.persistence.runtime;

import com.ladakx.inertia.physics.persistence.DynamicBodyPersistenceCoordinator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Objects;

public final class DynamicBodyChunkListener implements Listener {

    private final DynamicBodyPersistenceCoordinator coordinator;

    public DynamicBodyChunkListener(DynamicBodyPersistenceCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        coordinator.onChunkLoad(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }
}
