package com.ladakx.inertia.common.chunk;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import org.bukkit.Chunk;
import org.bukkit.World;
import java.util.HashSet;
import java.util.Set;

public class ChunkTicketManager {
    private final World world;
    private final Set<Long> forcedChunks = new HashSet<>();

    public ChunkTicketManager(World world) {
        this.world = world;
    }

    public void updateTickets(Set<Long> activeChunkKeys) {
        forcedChunks.removeIf(key -> {
            if (!activeChunkKeys.contains(key)) {
                setChunkTicket(key, false);
                return true;
            }
            return false;
        });

        // Добавляем тикеты для чанков, где есть тела
        for (Long key : activeChunkKeys) {
            if (forcedChunks.add(key)) {
                setChunkTicket(key, true);
            }
        }
    }

    public void releaseAll() {
        for (Long key : forcedChunks) {
            setChunkTicket(key, false);
        }
        forcedChunks.clear();
    }

    private void setChunkTicket(long key, boolean add) {
        int x = ChunkUtils.getChunkX(key);
        int z = ChunkUtils.getChunkZ(key);
        try {
            if (add) {
                world.addPluginChunkTicket(x, z, InertiaPlugin.getInstance());
            } else {
                world.removePluginChunkTicket(x, z, InertiaPlugin.getInstance());
            }
        } catch (Exception e) {
            InertiaLogger.error("Failed to update ticket state for chunk [" + x + ", " + z + "] in world " + world.getName(), e);
        }
    }
}