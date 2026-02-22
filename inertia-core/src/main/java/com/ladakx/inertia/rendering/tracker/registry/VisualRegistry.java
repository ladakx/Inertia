package com.ladakx.inertia.rendering.tracker.registry;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.tracker.grid.ChunkGridIndex;
import com.ladakx.inertia.rendering.tracker.state.TrackedVisual;
import com.ladakx.inertia.rendering.version.ClientVersionRange;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public final class VisualRegistry {
    private final Map<Integer, TrackedVisual> visualsById;
    private final ChunkGridIndex chunkGrid;
    private final VisualTokenService tokenService;
    private final VisualTombstoneService tombstoneService;

    public VisualRegistry(Map<Integer, TrackedVisual> visualsById,
                          ChunkGridIndex chunkGrid,
                          VisualTokenService tokenService,
                          VisualTombstoneService tombstoneService) {
        this.visualsById = Objects.requireNonNull(visualsById, "visualsById");
        this.chunkGrid = Objects.requireNonNull(chunkGrid, "chunkGrid");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.tombstoneService = Objects.requireNonNull(tombstoneService, "tombstoneService");
    }

    public void register(NetworkVisual visual, Location location, Quaternionf rotation) {
        register(visual, location, rotation, null);
    }

    public void register(NetworkVisual visual, Location location, Quaternionf rotation, @Nullable ClientVersionRange clientRange) {
        register(visual, location, rotation, clientRange, 0x07, true);
    }

    public void register(NetworkVisual visual,
                         Location location,
                         Quaternionf rotation,
                         @Nullable ClientVersionRange clientRange,
                         int allowedLodMask,
                         boolean enabled) {
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");

        tombstoneService.clear(visual.getId());
        tokenService.bump(visual.getId());

        TrackedVisual tracked = new TrackedVisual(visual, location.clone(), new Quaternionf(rotation), clientRange, allowedLodMask, enabled);
        visualsById.put(visual.getId(), tracked);
        chunkGrid.add(visual.getId(), location);
    }

    public boolean updateState(NetworkVisual visual, Location location, Quaternionf rotation, boolean enabled, long tickCounter) {
        if (tombstoneService.isTombstoned(visual.getId(), tickCounter)) {
            return false;
        }

        TrackedVisual tracked = visualsById.get(visual.getId());
        if (tracked == null) {
            register(visual, location, rotation);
            return false;
        }

        long oldChunkKey = ChunkUtils.getChunkKey(tracked.location().getBlockX() >> 4, tracked.location().getBlockZ() >> 4);
        long newChunkKey = ChunkUtils.getChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);

        tracked.update(location, rotation);
        boolean enabledChanged = tracked.isEnabled() != enabled;
        if (enabledChanged) {
            tracked.setEnabled(enabled);
            tokenService.bump(visual.getId());
        }

        if (oldChunkKey != newChunkKey) {
            chunkGrid.remove(visual.getId(), oldChunkKey);
            chunkGrid.add(visual.getId(), newChunkKey);
        }
        return enabledChanged;
    }

    public void markMetaDirty(NetworkVisual visual, boolean critical) {
        TrackedVisual tracked = visualsById.get(visual.getId());
        if (tracked != null) {
            tracked.markMetaDirty(critical);
        }
    }

    public void beginTick() {
        for (TrackedVisual tracked : visualsById.values()) {
            tracked.beginTick();
        }
    }

    public boolean isClosed(int visualId, long tickCounter) {
        return tombstoneService.isTombstoned(visualId, tickCounter);
    }
}
