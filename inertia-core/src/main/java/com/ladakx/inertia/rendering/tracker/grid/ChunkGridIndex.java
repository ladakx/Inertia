package com.ladakx.inertia.rendering.tracker.grid;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import org.bukkit.Location;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkGridIndex {
    private final Map<Long, Set<Integer>> grid = new ConcurrentHashMap<>();

    public void add(int id, Location loc) {
        long key = ChunkUtils.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        add(id, key);
    }

    public void add(int id, long chunkKey) {
        grid.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    public void remove(int id, Location loc) {
        long key = ChunkUtils.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        remove(id, key);
    }

    public void remove(int id, long chunkKey) {
        Set<Integer> set = grid.get(chunkKey);
        if (set == null) {
            return;
        }
        set.remove(id);
        if (set.isEmpty()) {
            grid.remove(chunkKey);
        }
    }

    public Set<Integer> get(long chunkKey) {
        return grid.get(chunkKey);
    }

    public void clear() {
        grid.clear();
    }
}
