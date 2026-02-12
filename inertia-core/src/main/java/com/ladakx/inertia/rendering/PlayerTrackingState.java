package com.ladakx.inertia.rendering;

import com.ladakx.inertia.common.chunk.ChunkUtils;

import java.util.*;

final class PlayerTrackingState {
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

    int chunkX() { return chunkX; }
    int chunkZ() { return chunkZ; }
    UUID worldId() { return worldId; }
    long lastFullRecalcTick() { return lastFullRecalcTick; }
    boolean initialized() { return initialized; }
    Set<Integer> visibleIds() { return visibleIds; }

    void updatePosition(int chunkX, int chunkZ, UUID worldId) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.worldId = worldId;
        this.initialized = true;
    }

    void rebuildCandidates(ChunkGridIndex chunkGrid, int centerChunkX, int centerChunkZ, int viewDistanceChunks) {
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

    void markFullRecalcDone(long tick) {
        this.lastFullRecalcTick = tick;
    }

    void markVisibilityDirty() {
        this.visibilityDirty = true;
        this.candidateCursor = 0;
    }

    boolean needsVisibilityPass() {
        return visibilityDirty;
    }

    void markVisibilityPassComplete() {
        this.visibilityDirty = false;
    }

    Integer nextCandidateId() {
        if (candidateCursor >= precomputedCandidateIds.size()) {
            return null;
        }
        return precomputedCandidateIds.get(candidateCursor++);
    }

    Integer nextVisibleId() {
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

    void resetVisibleCursor() {
        this.visibleCursor = 0;
    }

    int remainingVisibleChecks() {
        return Math.max(0, visibleIterationOrder.size() - visibleCursor);
    }

    boolean addVisible(int id) {
        boolean added = visibleIds.add(id);
        if (added) {
            visibleIterationDirty = true;
        }
        return added;
    }

    boolean removeVisible(int id) {
        boolean removed = visibleIds.remove(id);
        if (removed) {
            visibleIterationDirty = true;
        }
        return removed;
    }

    void clearVisible() {
        if (!visibleIds.isEmpty()) {
            visibleIds.clear();
            visibleIterationDirty = true;
        }
        resetVisibleCursor();
    }

    void markVisibleIterationDirty() {
        this.visibleIterationDirty = true;
    }

    boolean tryMarkVisibilityTaskQueued() {
        if (visibilityTaskQueued) {
            return false;
        }
        visibilityTaskQueued = true;
        return true;
    }

    void markVisibilityTaskDone() {
        visibilityTaskQueued = false;
    }

    boolean tryMarkTransformTaskQueued() {
        if (transformTaskQueued) {
            return false;
        }
        transformTaskQueued = true;
        return true;
    }

    void markTransformTaskDone() {
        transformTaskQueued = false;
    }
}
