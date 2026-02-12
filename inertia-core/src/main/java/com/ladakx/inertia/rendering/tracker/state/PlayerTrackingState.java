package com.ladakx.inertia.rendering.tracker.state;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.rendering.tracker.grid.ChunkGridIndex;

import java.util.*;

public final class PlayerTrackingState {
    private int chunkX;
    private int chunkZ;
    private UUID worldId;
    private long lastFullRecalcTick;
    private boolean initialized;

    private final Set<Integer> visibleIds = new HashSet<>();
    private final List<Integer> visibleIterationOrder = new ArrayList<>();
    private final List<Integer> precomputedCandidateIds = new ArrayList<>();
    private final Set<Integer> candidateLookup = new HashSet<>();
    private int candidateCursor = 0;
    private int visibleCursor = 0;
    private boolean visibilityDirty;
    private boolean visibleIterationDirty;
    private boolean visibilityTaskQueued;
    private boolean transformTaskQueued;

    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public UUID worldId() { return worldId; }
    public long lastFullRecalcTick() { return lastFullRecalcTick; }
    public boolean initialized() { return initialized; }
    public Set<Integer> visibleIds() { return visibleIds; }

    public void updatePosition(int chunkX, int chunkZ, UUID worldId) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.worldId = worldId;
        this.initialized = true;
    }

    public void rebuildCandidates(ChunkGridIndex chunkGrid, int centerChunkX, int centerChunkZ, int viewDistanceChunks) {
        candidateLookup.clear();
        precomputedCandidateIds.clear();
        for (int x = -viewDistanceChunks; x <= viewDistanceChunks; x++) {
            for (int z = -viewDistanceChunks; z <= viewDistanceChunks; z++) {
                long chunkKey = ChunkUtils.getChunkKey(centerChunkX + x, centerChunkZ + z);
                Set<Integer> ids = chunkGrid.get(chunkKey);
                if (ids != null && !ids.isEmpty()) {
                    for (Integer id : ids) {
                        if (candidateLookup.add(id)) {
                            precomputedCandidateIds.add(id);
                        }
                    }
                }
            }
        }

        candidateCursor = 0;
        if (visibleIds.removeIf(id -> !candidateLookup.contains(id))) {
            visibleIterationDirty = true;
        }
        resetVisibleCursor();
    }

    public void markFullRecalcDone(long tick) {
        this.lastFullRecalcTick = tick;
    }

    public void markVisibilityDirty() {
        this.visibilityDirty = true;
        this.candidateCursor = 0;
    }

    public boolean needsVisibilityPass() {
        return visibilityDirty;
    }

    public void markVisibilityPassComplete() {
        this.visibilityDirty = false;
    }

    public Integer nextCandidateId() {
        if (candidateCursor >= precomputedCandidateIds.size()) {
            return null;
        }
        return precomputedCandidateIds.get(candidateCursor++);
    }

    public Integer nextVisibleId() {
        if (visibleIterationDirty) {
            visibleIterationOrder.clear();
            visibleIterationOrder.addAll(visibleIds);
            visibleIterationDirty = false;
        }

        int size = visibleIterationOrder.size();
        if (size == 0) {
            return null;
        }

        if (visibleCursor >= size) {
            visibleCursor = 0;
        }

        return visibleIterationOrder.get(visibleCursor++);
    }

    public void resetVisibleCursor() {
        this.visibleCursor = 0;
    }

    public int remainingVisibleChecks() {
        return Math.max(0, visibleIterationOrder.size() - visibleCursor);
    }

    public boolean addVisible(int id) {
        boolean added = visibleIds.add(id);
        if (added) {
            visibleIterationDirty = true;
        }
        return added;
    }

    public boolean removeVisible(int id) {
        boolean removed = visibleIds.remove(id);
        if (removed) {
            visibleIterationDirty = true;
        }
        return removed;
    }

    public void clearVisible() {
        if (!visibleIds.isEmpty()) {
            visibleIds.clear();
            visibleIterationDirty = true;
        }
        resetVisibleCursor();
    }

    public void markVisibleIterationDirty() {
        this.visibleIterationDirty = true;
    }

    public boolean tryMarkVisibilityTaskQueued() {
        if (visibilityTaskQueued) {
            return false;
        }
        visibilityTaskQueued = true;
        return true;
    }

    public void markVisibilityTaskDone() {
        visibilityTaskQueued = false;
    }

    public boolean tryMarkTransformTaskQueued() {
        if (transformTaskQueued) {
            return false;
        }
        transformTaskQueued = true;
        return true;
    }

    public void markTransformTaskDone() {
        transformTaskQueued = false;
    }

    public void forceRecalc() {
        this.lastFullRecalcTick = -1;
    }
}
