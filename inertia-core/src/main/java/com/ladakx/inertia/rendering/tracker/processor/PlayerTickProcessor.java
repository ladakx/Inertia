package com.ladakx.inertia.rendering.tracker.processor;

import com.ladakx.inertia.rendering.tracker.grid.ChunkGridIndex;
import com.ladakx.inertia.rendering.tracker.state.PlayerFrame;
import com.ladakx.inertia.rendering.tracker.state.PlayerTrackingState;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerTickProcessor {

    @FunctionalInterface
    public interface SliceEnqueuer {
        void enqueue(UUID playerId, PlayerTrackingState trackingState, double viewDistanceSquared);
    }

    private final Map<UUID, PlayerTrackingState> playerTrackingStates;
    private final ChunkGridIndex chunkGrid;
    private final SliceEnqueuer visibilityEnqueuer;
    private final SliceEnqueuer transformEnqueuer;

    public PlayerTickProcessor(Map<UUID, PlayerTrackingState> playerTrackingStates,
                        ChunkGridIndex chunkGrid,
                        SliceEnqueuer visibilityEnqueuer,
                        SliceEnqueuer transformEnqueuer) {
        this.playerTrackingStates = Objects.requireNonNull(playerTrackingStates, "playerTrackingStates");
        this.chunkGrid = Objects.requireNonNull(chunkGrid, "chunkGrid");
        this.visibilityEnqueuer = Objects.requireNonNull(visibilityEnqueuer, "visibilityEnqueuer");
        this.transformEnqueuer = Objects.requireNonNull(transformEnqueuer, "transformEnqueuer");
    }

    public void processPlayers(Collection<PlayerFrame> players,
                        double viewDistanceSquared,
                        int viewDistanceChunks,
                        long tickCounter,
                        int fullRecalcIntervalTicks,
                        boolean destroyDrainFastPathActive) {
        for (PlayerFrame player : players) {
            if (player == null) continue;
            UUID playerId = player.playerId();
            PlayerTrackingState trackingState = playerTrackingStates.computeIfAbsent(playerId, k -> new PlayerTrackingState());

            int pChunkX = player.chunkX();
            int pChunkZ = player.chunkZ();
            UUID playerWorldId = player.worldId();
            boolean chunkChanged = !trackingState.initialized() || pChunkX != trackingState.chunkX() || pChunkZ != trackingState.chunkZ();
            boolean worldChanged = !trackingState.initialized() || !playerWorldId.equals(trackingState.worldId());
            boolean periodicFullRecalc = !trackingState.initialized()
                    || (tickCounter - trackingState.lastFullRecalcTick()) >= fullRecalcIntervalTicks;
            boolean requiresFullRecalc = chunkChanged || worldChanged || periodicFullRecalc;

            trackingState.updatePosition(pChunkX, pChunkZ, playerWorldId);

            if (requiresFullRecalc) {
                trackingState.rebuildCandidates(chunkGrid, pChunkX, pChunkZ, viewDistanceChunks);
                trackingState.markFullRecalcDone(tickCounter);
                trackingState.markVisibilityDirty();
            }

            if (!destroyDrainFastPathActive && trackingState.needsVisibilityPass()) {
                visibilityEnqueuer.enqueue(playerId, trackingState, viewDistanceSquared);
            }

            transformEnqueuer.enqueue(playerId, trackingState, viewDistanceSquared);
        }

    }
}
