package com.ladakx.inertia.rendering;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkEntityTracker {

    private final Map<Integer, TrackedVisual> visualsById = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerTrackingState> playerTrackingStates = new ConcurrentHashMap<>();
    private final Map<Long, Set<Integer>> chunkGrid = new ConcurrentHashMap<>();

    private final Map<UUID, List<Object>> packetBuffer = new ConcurrentHashMap<>();

    // Default values matched with InertiaConfig defaults (0.01^2 and 0.3)
    private volatile float posThresholdSq = 0.0001f;
    private volatile float rotThresholdDot = 0.3f;
    private volatile int maxVisibilityUpdatesPerPlayerPerTick = 256;
    private volatile int fullRecalcIntervalTicks = 20;
    private long tickCounter = 0L;

    private final PacketFactory packetFactory;

    public NetworkEntityTracker(PacketFactory packetFactory) {
        this.packetFactory = packetFactory;
    }

    public NetworkEntityTracker(PacketFactory packetFactory, InertiaConfig.RenderingSettings.NetworkEntityTrackerSettings settings) {
        this(packetFactory);
        applySettings(settings);
    }

    public void applySettings(InertiaConfig.RenderingSettings.NetworkEntityTrackerSettings settings) {
        if (settings == null) return;
        this.posThresholdSq = settings.posThresholdSq;
        this.rotThresholdDot = settings.rotThresholdDot;
        this.maxVisibilityUpdatesPerPlayerPerTick = settings.maxVisibilityUpdatesPerPlayerPerTick;
        this.fullRecalcIntervalTicks = settings.fullRecalcIntervalTicks;
    }

    public void register(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");

        TrackedVisual tracked = new TrackedVisual(visual, location.clone(), new Quaternionf(rotation));
        visualsById.put(visual.getId(), tracked);
        addToGrid(visual.getId(), location);
    }

    public void unregister(@NotNull NetworkVisual visual) {
        Objects.requireNonNull(visual, "visual");
        unregisterById(visual.getId());
    }

    public void unregisterById(int id) {
        TrackedVisual tracked = visualsById.remove(id);
        if (tracked != null) {
            removeFromGrid(id, tracked.location());
            NetworkVisual visual = tracked.visual();

            Object destroyPacket = visual.createDestroyPacket();

            Iterator<Map.Entry<UUID, PlayerTrackingState>> it = playerTrackingStates.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, PlayerTrackingState> entry = it.next();
                if (entry.getValue().visibleIds().remove(id)) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null) {
                        packetFactory.sendPacket(player, destroyPacket);
                    }
                }
            }
        }
    }

    public void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        TrackedVisual tracked = visualsById.get(visual.getId());
        if (tracked != null) {
            long oldChunkKey = ChunkUtils.getChunkKey(tracked.location().getBlockX() >> 4, tracked.location().getBlockZ() >> 4);
            long newChunkKey = ChunkUtils.getChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);

            tracked.update(location, rotation);

            if (oldChunkKey != newChunkKey) {
                removeFromGrid(visual.getId(), oldChunkKey);
                addToGrid(visual.getId(), newChunkKey);
            }
        } else {
            register(visual, location, rotation);
        }
    }

    public void updateMetadata(@NotNull NetworkVisual visual) {
        TrackedVisual tracked = visualsById.get(visual.getId());
        if (tracked != null) {
            tracked.markMetaDirty();
        }
    }

    public void tick(@NotNull Collection<? extends Player> players, double viewDistanceSquared) {
        tickCounter++;

        // Phase 1: Pre-calculate shared packets for all dirty entities
        for (TrackedVisual tracked : visualsById.values()) {
            tracked.tickGlobal(posThresholdSq, rotThresholdDot);
        }

        int viewDistanceChunks = (int) Math.ceil(Math.sqrt(viewDistanceSquared) / 16.0);

        // Phase 2: Visibility & Queueing
        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;
            UUID playerId = player.getUniqueId();
            PlayerTrackingState trackingState = playerTrackingStates.computeIfAbsent(playerId, k -> new PlayerTrackingState());
            Set<Integer> visible = trackingState.visibleIds();

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
            }

            if (trackingState.needsWorkQueueRebuild()) {
                trackingState.rebuildWorkQueue();
            }

            int budget = maxVisibilityUpdatesPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxVisibilityUpdatesPerPlayerPerTick;
            int processed = 0;
            while (processed < budget) {
                Integer id = trackingState.nextWorkEntityId();
                if (id == null) break;

                TrackedVisual tracked = visualsById.get(id);
                if (tracked == null) {
                    visible.remove(id);
                    processed++;
                    continue;
                }

                Location trackedLoc = tracked.location();
                World trackedWorld = trackedLoc.getWorld();
                boolean sameWorld = trackedWorld != null && playerWorldId.equals(trackedWorld.getUID());

                double dx = trackedLoc.getX() - playerLoc.getX();
                double dy = trackedLoc.getY() - playerLoc.getY();
                double dz = trackedLoc.getZ() - playerLoc.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;

                boolean inRange = sameWorld && distSq <= viewDistanceSquared;

                if (inRange) {
                    if (visible.add(id)) {
                        bufferPacket(playerId, tracked.visual().createSpawnPacket(tracked.location(), tracked.rotation()));
                        tracked.markSent(player);
                    } else {
                        Object updatePacket = tracked.getPendingUpdatePacket();
                        if (updatePacket != null) {
                            bufferPacket(playerId, updatePacket);
                            tracked.markSent(player);
                        }

                        Object metaPacket = tracked.getPendingMetaPacket();
                        if (metaPacket != null) {
                            bufferPacket(playerId, metaPacket);
                        }
                    }
                } else if (visible.remove(id)) {
                    bufferPacket(playerId, tracked.visual().createDestroyPacket());
                }

                processed++;
            }
        }

        playerTrackingStates.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);

        // Phase 3: Flush
        flushPackets();
    }

    private void bufferPacket(UUID playerId, Object packet) {
        if (packet == null) return;
        packetBuffer.computeIfAbsent(playerId, k -> new ArrayList<>()).add(packet);
    }

    private void flushPackets() {
        if (packetFactory == null) return;

        for (Map.Entry<UUID, List<Object>> entry : packetBuffer.entrySet()) {
            List<Object> queue = entry.getValue();
            if (queue.isEmpty()) continue;

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                packetFactory.sendBundle(player, new ArrayList<>(queue));
            }
            queue.clear();
        }
    }

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        packetBuffer.remove(uuid);
        PlayerTrackingState trackingState = playerTrackingStates.remove(uuid);
        Set<Integer> visible = trackingState != null ? trackingState.visibleIds() : null;
        if (visible != null) {
            for (Integer id : visible) {
                TrackedVisual tracked = visualsById.get(id);
                if (tracked != null) {
                    packetFactory.sendPacket(player, tracked.visual().createDestroyPacket());
                }
            }
        }
    }

    public void clear() {
        visualsById.clear();
        playerTrackingStates.clear();
        chunkGrid.clear();
        packetBuffer.clear();
    }

    private static class PlayerTrackingState {
        private int chunkX;
        private int chunkZ;
        private UUID worldId;
        private long lastFullRecalcTick;
        private boolean initialized;

        private final Set<Integer> visibleIds = ConcurrentHashMap.newKeySet();
        private final List<Integer> precomputedCandidateIds = new ArrayList<>();
        private final List<Integer> workQueue = new ArrayList<>();
        private int workCursor = 0;

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

        public void rebuildCandidates(Map<Long, Set<Integer>> chunkGrid, int centerChunkX, int centerChunkZ, int viewDistanceChunks) {
            LinkedHashSet<Integer> accumulator = new LinkedHashSet<>();
            for (int x = -viewDistanceChunks; x <= viewDistanceChunks; x++) {
                for (int z = -viewDistanceChunks; z <= viewDistanceChunks; z++) {
                    long chunkKey = ChunkUtils.getChunkKey(centerChunkX + x, centerChunkZ + z);
                    Set<Integer> ids = chunkGrid.get(chunkKey);
                    if (ids != null && !ids.isEmpty()) {
                        accumulator.addAll(ids);
                    }
                }
            }

            precomputedCandidateIds.clear();
            precomputedCandidateIds.addAll(accumulator);
            workCursor = 0;
        }

        public void markFullRecalcDone(long tick) {
            this.lastFullRecalcTick = tick;
        }

        public boolean needsWorkQueueRebuild() {
            return workCursor >= workQueue.size();
        }

        public void rebuildWorkQueue() {
            LinkedHashSet<Integer> queueUnion = new LinkedHashSet<>(precomputedCandidateIds);
            queueUnion.addAll(visibleIds);

            workQueue.clear();
            workQueue.addAll(queueUnion);
            workCursor = 0;
        }

        public Integer nextWorkEntityId() {
            if (workCursor >= workQueue.size()) {
                return null;
            }
            return workQueue.get(workCursor++);
        }
    }

    private void addToGrid(int id, Location loc) {
        long key = ChunkUtils.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        addToGrid(id, key);
    }

    private void addToGrid(int id, long chunkKey) {
        chunkGrid.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    private void removeFromGrid(int id, Location loc) {
        long key = ChunkUtils.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        removeFromGrid(id, key);
    }

    private void removeFromGrid(int id, long chunkKey) {
        Set<Integer> set = chunkGrid.get(chunkKey);
        if (set != null) {
            set.remove(id);
            if (set.isEmpty()) {
                chunkGrid.remove(chunkKey);
            }
        }
    }

    private static class TrackedVisual {
        private final NetworkVisual visual;
        private final Location location;
        private final Quaternionf rotation;

        private final Vector3f syncedPos = new Vector3f();
        private final Quaternionf syncedRot = new Quaternionf();

        private Object cachedUpdatePacket = null;
        private Object cachedMetaPacket = null;
        private boolean metaDirty = false;

        private TrackedVisual(NetworkVisual visual, Location location, Quaternionf rotation) {
            this.visual = visual;
            this.location = location;
            this.rotation = rotation;
            sync();
        }

        public NetworkVisual visual() { return visual; }
        public Location location() { return location; }
        public Quaternionf rotation() { return rotation; }

        public void update(Location newLoc, Quaternionf newRot) {
            this.location.setWorld(newLoc.getWorld());
            this.location.setX(newLoc.getX());
            this.location.setY(newLoc.getY());
            this.location.setZ(newLoc.getZ());
            this.location.setYaw(newLoc.getYaw());
            this.location.setPitch(newLoc.getPitch());
            this.rotation.set(newRot);
        }

        public void markMetaDirty() {
            this.metaDirty = true;
        }

        public void tickGlobal(float posThresholdSq, float rotThresholdDot) {
            this.cachedUpdatePacket = null;
            this.cachedMetaPacket = null;

            // Movement Check
            float dx = (float)location.getX() - syncedPos.x;
            float dy = (float)location.getY() - syncedPos.y;
            float dz = (float)location.getZ() - syncedPos.z;
            float distSq = dx*dx + dy*dy + dz*dz;

            boolean moved = distSq > posThresholdSq;
            boolean rotated = false;

            if (!moved) {
                float dot = Math.abs(rotation.dot(syncedRot));
                rotated = dot < rotThresholdDot;
            }

            if (moved || rotated) {
                this.cachedUpdatePacket = visual.createTeleportPacket(location, rotation);
                sync();
            }

            // Metadata Check
            if (this.metaDirty) {
                this.cachedMetaPacket = visual.createMetadataPacket();
                this.metaDirty = false;
            }
        }

        public Object getPendingUpdatePacket() {
            return cachedUpdatePacket;
        }

        public Object getPendingMetaPacket() {
            return cachedMetaPacket;
        }

        public void markSent(Player player) {
        }

        private void sync() {
            this.syncedPos.set((float)location.getX(), (float)location.getY(), (float)location.getZ());
            this.syncedRot.set(rotation);
        }
    }
}
