package com.ladakx.inertia.core.impl.rendering;

import com.ladakx.inertia.api.rendering.entity.RenderEntity;
import com.ladakx.inertia.api.rendering.entity.TransformStack;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

final class RenderEntityImpl implements RenderEntity {

    private final NetworkEntityTracker tracker;
    private final NetworkVisual visual;
    @SuppressWarnings("unused")
    private final String key;

    private final TransformStack transformStack = new TransformStack();

    private final Vector3f defOffset = new Vector3f();
    private final Quaternionf defRotation = new Quaternionf();
    private final boolean syncPosition;
    private final boolean syncRotation;

    private final Vector3f basePos = new Vector3f();
    private final Quaternionf baseRot = new Quaternionf();

    private boolean enabled = true;
    private boolean closed = false;

    // Cached composed state
    private final Vector3f stackPos = new Vector3f();
    private final Quaternionf stackRot = new Quaternionf();
    private final Vector3f localPos = new Vector3f();
    private final Quaternionf localRot = new Quaternionf();
    private final Vector3f worldPos = new Vector3f();
    private final Quaternionf worldRot = new Quaternionf();
    private final Vector3f scratchVec = new Vector3f();

    // Objects passed to tracker (mutated in-place to avoid allocations)
    private final Location trackerLocation;
    private final Quaternionf trackerRotation = new Quaternionf();

    RenderEntityImpl(@NotNull NetworkEntityTracker tracker,
                     @NotNull NetworkVisual visual,
                     @NotNull String key,
                     @NotNull Vector localOffset,
                     @NotNull Quaternionf localRotation,
                     boolean syncPosition,
                     boolean syncRotation) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.visual = Objects.requireNonNull(visual, "visual");
        this.key = Objects.requireNonNull(key, "key");
        Objects.requireNonNull(localOffset, "localOffset");
        Objects.requireNonNull(localRotation, "localRotation");
        this.syncPosition = syncPosition;
        this.syncRotation = syncRotation;
        this.defOffset.set((float) localOffset.getX(), (float) localOffset.getY(), (float) localOffset.getZ());
        this.defRotation.set(localRotation);

        // world will be set in setBaseTransform()
        this.trackerLocation = new Location(null, 0, 0, 0);
        recompute();
    }

    @Override
    public @NotNull NetworkVisual visual() {
        return visual;
    }

    @Override
    public @NotNull TransformStack transforms() {
        return transformStack;
    }

    @Override
    public void setBaseTransform(@NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("location.world is null");
        }
        basePos.set((float) location.getX(), (float) location.getY(), (float) location.getZ());
        baseRot.set(rotation);
        trackerLocation.setWorld(world);
        recompute();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void sync() {
        if (closed) return;
        recompute();
        tracker.updateState(visual, trackerLocation, trackerRotation, enabled);
    }

    @Override
    public void syncMetadata(boolean critical) {
        if (closed) return;
        tracker.updateMetadata(visual, critical);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        tracker.unregister(visual);
    }

    Location trackerLocation() {
        return trackerLocation;
    }

    Quaternionf trackerRotation() {
        return trackerRotation;
    }

    NetworkEntityTracker.VisualStateUpdate stateUpdate() {
        return new NetworkEntityTracker.VisualStateUpdate(visual, trackerLocation, trackerRotation, enabled);
    }

    NetworkEntityTracker tracker() {
        return tracker;
    }

    NetworkEntityTracker.VisualStateUpdate prepareStateUpdate() {
        recompute();
        return stateUpdate();
    }

    private void recompute() {
        transformStack.getLocalTranslation(stackPos);
        transformStack.getLocalRotation(stackRot);

        // localRot = defRot * stackRot
        localRot.set(defRotation).mul(stackRot);

        // localPos = (syncPosition ? defOffset : 0) + defRot * stackPos
        localPos.zero();
        if (syncPosition) {
            localPos.add(defOffset);
        }
        scratchVec.set(stackPos);
        defRotation.transform(scratchVec);
        localPos.add(scratchVec);

        // worldPos = basePos + baseRot * localPos
        worldPos.set(localPos);
        baseRot.transform(worldPos);
        worldPos.add(basePos);

        // worldRot = (syncRotation ? baseRot : I) * localRot
        if (syncRotation) {
            worldRot.set(baseRot).mul(localRot);
        } else {
            worldRot.set(localRot);
        }

        trackerLocation.setX(worldPos.x);
        trackerLocation.setY(worldPos.y);
        trackerLocation.setZ(worldPos.z);
        trackerRotation.set(worldRot);
    }
}
