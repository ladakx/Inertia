package com.ladakx.inertia.rendering;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class PlayerTickProcessor {

    @FunctionalInterface
    interface SliceEnqueuer {
        void enqueue(UUID playerId, PlayerTrackingState trackingState, double viewDistanceSquared);
    }

    private final Map<UUID, PlayerTrackingState> playerTrackingStates;
    private final ChunkGridIndex chunkGrid;
    private final SliceEnqueuer visibilityEnqueuer;
    private final SliceEnqueuer transformEnqueuer;

    PlayerTickProcessor(Map<UUID, PlayerTrackingState> playerTrackingStates,
                        ChunkGridIndex chunkGrid,
                        SliceEnqueuer visibilityEnqueuer,
                        SliceEnqueuer transformEnqueuer) {
        this.playerTrackingStates = Objects.requireNonNull(playerTrackingStates, "playerTrackingStates");
        this.chunkGrid = Objects.requireNonNull(chunkGrid, "chunkGrid");
        this.visibilityEnqueuer = Objects.requireNonNull(visibilityEnqueuer, "visibilityEnqueuer");
        this.transformEnqueuer = Objects.requireNonNull(transformEnqueuer, "transformEnqueuer");
    }

    void processPlayers(Collection<? extends Player> players,
                        double viewDistanceSquared,
                        int viewDistanceChunks,
                        long tickCounter,
                        int fullRecalcIntervalTicks,
                        boolean destroyDrainFastPathActive) {
        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;
            UUID playerId = player.getUniqueId();
            PlayerTrackingState trackingState = playerTrackingStates.computeIfAbsent(playerId, k -> new PlayerTrackingState());

            Location playerLoc = player.getLocation();
            int pChunkX = playerLoc.getBlockX() >> 4;
            int pChunkZ = playerLoc.getBlockZ() >> 4;
            World playerWorld = playerLoc.getWorld();
            if (playerWorld == null) continue;

            UUID playerWorldId = playerWorld.getUID();
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

        playerTrackingStates.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
    }
}

