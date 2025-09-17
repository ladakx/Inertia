package com.ladakx.inertia.core.world.listeners;

import com.ladakx.inertia.core.world.ChunkManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkListener implements Listener {

    private final ChunkManager chunkManager;

    public ChunkListener(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        chunkManager.loadChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        chunkManager.unloadChunk(event.getChunk());
    }
}

