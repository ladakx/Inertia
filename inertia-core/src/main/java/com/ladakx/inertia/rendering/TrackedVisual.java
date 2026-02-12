package com.ladakx.inertia.rendering;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class TrackedVisual {
    private final NetworkVisual visual;
    private final Location location;
    private final Quaternionf rotation;

    private final SyncState nearSyncState = new SyncState();
    private final SyncState midSyncState = new SyncState();
    private final SyncState farSyncState = new SyncState();

    private long lastMidUpdateTick = Long.MIN_VALUE;
    private long lastFarUpdateTick = Long.MIN_VALUE;

    private Object cachedPositionPacket = null;
    private Object cachedTransformMetaPacket = null;
    private PendingMetadata cachedMetaPacket = null;
    private boolean metadataDirty = false;
    private boolean criticalMetaDirty = false;
    private boolean forceTransformResyncDirty = false;

    TrackedVisual(NetworkVisual visual, Location location, Quaternionf rotation) {
        this.visual = visual;
        this.location = location;
        this.rotation = rotation;
        syncAll();
    }

    NetworkVisual visual() { return visual; }
    Location location() { return location; }
    Quaternionf rotation() { return rotation; }

    void update(Location newLoc, Quaternionf newRot) {
        this.location.setWorld(newLoc.getWorld());
        this.location.setX(newLoc.getX());
        this.location.setY(newLoc.getY());
        this.location.setZ(newLoc.getZ());
        this.location.setYaw(newLoc.getYaw());
        this.location.setPitch(newLoc.getPitch());
        this.rotation.set(newRot);
    }

    void markMetaDirty(boolean critical) {
        this.metadataDirty = true;
        this.criticalMetaDirty = this.criticalMetaDirty || critical;
        if (critical) {
            this.forceTransformResyncDirty = true;
        }
    }

    void beginTick() {
        this.cachedPositionPacket = null;
        this.cachedTransformMetaPacket = null;
        this.cachedMetaPacket = null;

        if (this.metadataDirty) {
            this.cachedMetaPacket = new PendingMetadata(visual.createMetadataPacket(), criticalMetaDirty);
            this.metadataDirty = false;
            this.criticalMetaDirty = false;
        }
    }

    boolean hasSignificantNearChange(float nearPosThresholdSq, float nearRotThresholdDot) {
        return isPositionChanged(nearSyncState, nearPosThresholdSq) || isRotationChanged(nearSyncState, nearRotThresholdDot);
    }

    UpdateDecision prepareUpdate(
            LodLevel lodLevel,
            long tick,
            float nearPosThresholdSq,
            float nearRotThresholdDot,
            float midPosThresholdSq,
            float midRotThresholdDot,
            int midIntervalTicks,
            float farPosThresholdSq,
            float farRotThresholdDot,
            int farIntervalTicks
    ) {
        return switch (lodLevel) {
            case NEAR -> prepareNearUpdate(nearPosThresholdSq, nearRotThresholdDot);
            case MID -> prepareLodUpdate(midSyncState, tick, midPosThresholdSq, midRotThresholdDot, midIntervalTicks, true);
            case FAR -> prepareLodUpdate(farSyncState, tick, farPosThresholdSq, farRotThresholdDot, farIntervalTicks, false);
        };
    }

    private UpdateDecision prepareNearUpdate(float posThresholdSq, float rotThresholdDot) {
        boolean positionChanged = isPositionChanged(nearSyncState, posThresholdSq);
        boolean transformChanged = isRotationChanged(nearSyncState, rotThresholdDot);
        boolean forcedTransform = forceTransformResyncDirty;

        if (!positionChanged && !transformChanged && !forcedTransform) {
            return UpdateDecision.NONE;
        }

        if (positionChanged) {
            emitPosition(nearSyncState);
        }
        if (transformChanged || forcedTransform) {
            emitTransformMetadata(nearSyncState);
            forceTransformResyncDirty = false;
        }

        return new UpdateDecision(positionChanged, transformChanged || forcedTransform, forcedTransform);
    }

    private UpdateDecision prepareLodUpdate(SyncState state,
                                           long tick,
                                           float posThresholdSq,
                                           float rotThresholdDot,
                                           int intervalTicks,
                                           boolean midLod) {
        boolean positionChanged = isPositionChanged(state, posThresholdSq);
        boolean transformChanged = isRotationChanged(state, rotThresholdDot);
        boolean forcedTransform = forceTransformResyncDirty;
        boolean intervalReached = isIntervalReached(midLod ? lastMidUpdateTick : lastFarUpdateTick, tick, intervalTicks);

        boolean sendPosition = positionChanged && intervalReached;
        boolean sendTransform = forcedTransform || transformChanged || intervalReached;

        if (!sendPosition && !sendTransform) {
            return UpdateDecision.NONE;
        }

        if (sendPosition) {
            emitPosition(state);
        }
        if (sendTransform) {
            emitTransformMetadata(state);
            forceTransformResyncDirty = false;
        }

        if (midLod) {
            lastMidUpdateTick = tick;
        } else {
            lastFarUpdateTick = tick;
        }

        return new UpdateDecision(sendPosition, sendTransform, forcedTransform);
    }

    private void emitPosition(SyncState state) {
        if (this.cachedPositionPacket == null) {
            this.cachedPositionPacket = visual.createPositionPacket(location, rotation);
        }
        syncPosition(state);
    }

    private void emitTransformMetadata(SyncState state) {
        if (this.cachedTransformMetaPacket == null) {
            this.cachedTransformMetaPacket = visual.createTransformMetadataPacket(rotation);
        }
        syncRotation(state);
    }

    private boolean isPositionChanged(SyncState state, float posThresholdSq) {
        float dx = (float) location.getX() - state.pos.x;
        float dy = (float) location.getY() - state.pos.y;
        float dz = (float) location.getZ() - state.pos.z;
        float distSq = dx * dx + dy * dy + dz * dz;
        return distSq > posThresholdSq;
    }

    private boolean isRotationChanged(SyncState state, float rotThresholdDot) {
        float dot = Math.abs(rotation.dot(state.rot));
        return dot < rotThresholdDot;
    }

    private boolean isIntervalReached(long lastTick, long currentTick, int intervalTicks) {
        if (lastTick == Long.MIN_VALUE) {
            return true;
        }
        return (currentTick - lastTick) >= intervalTicks;
    }

    Object getPendingPositionPacket() {
        return cachedPositionPacket;
    }

    Object getPendingTransformMetaPacket() {
        return cachedTransformMetaPacket;
    }

    PendingMetadata getPendingMetaPacket() {
        return cachedMetaPacket;
    }

    void markSent(Player player) {
    }

    private void syncAll() {
        syncPosition(nearSyncState);
        syncRotation(nearSyncState);
        syncPosition(midSyncState);
        syncRotation(midSyncState);
        syncPosition(farSyncState);
        syncRotation(farSyncState);
    }

    private void syncPosition(SyncState state) {
        state.pos.set((float) location.getX(), (float) location.getY(), (float) location.getZ());
    }

    private void syncRotation(SyncState state) {
        state.rot.set(rotation);
    }

    private static final class SyncState {
        private final Vector3f pos = new Vector3f();
        private final Quaternionf rot = new Quaternionf();
    }
}
