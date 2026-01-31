package com.ladakx.inertia.common.chunk;

import com.ladakx.inertia.common.logging.InertiaLogger;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages forced loading of chunks for active physics bodies.
 * Must be accessed ONLY from the Main Thread.
 */
public class ChunkTicketManager {

    private final World world;
    private final Set<Long> forcedChunks = new HashSet<>();

    public ChunkTicketManager(World world) {
        this.world = world;
    }

    /**
     * Updates the force-loaded chunks based on the currently active physics bodies.
     *
     * @param activeChunkKeys Set of chunk keys that contain active bodies in the current tick.
     */
    public void updateTickets(Set<Long> activeChunkKeys) {
        // 1. Find chunks to release (present in forcedChunks but NOT in activeChunkKeys)
        // To avoid creating new sets every tick, we iterate manually or use a copy if necessary.
        // Optimization: Use removeIf for the local set logic.

        // Chunks to UNLOAD: forced - active
        forcedChunks.removeIf(key -> {
            if (!activeChunkKeys.contains(key)) {
                setForceLoaded(key, false);
                return true; // Remove from forcedChunks
            }
            return false; // Keep in forcedChunks
        });

        // 2. Find chunks to LOAD: active - forced
        for (Long key : activeChunkKeys) {
            if (forcedChunks.add(key)) { // Returns true if it was not present
                setForceLoaded(key, true);
            }
        }
    }

    /**
     * Releases all forced chunks (e.g. on server shutdown or world unload).
     */
    public void releaseAll() {
        for (Long key : forcedChunks) {
            setForceLoaded(key, false);
        }
        forcedChunks.clear();
    }

    private void setForceLoaded(long key, boolean force) {
        int x = ChunkUtils.getChunkX(key);
        int z = ChunkUtils.getChunkZ(key);
        
        try {
            // Check if chunk is loaded before getting it to avoid unnecessary sync loads if possible,
            // though setForceLoaded(true) implies loading.
            Chunk chunk = world.getChunkAt(x, z); 
            chunk.setForceLoaded(force);
        } catch (Exception e) {
            InertiaLogger.error("Failed to update force-load state for chunk [" + x + ", " + z + "] in world " + world.getName(), e);
        }
    }
}