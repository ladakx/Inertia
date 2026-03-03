package com.ladakx.inertia.core.impl.rendering;

import com.ladakx.inertia.api.rendering.entity.RenderEntity;
import com.ladakx.inertia.api.rendering.entity.TransformStack;
import com.ladakx.inertia.api.rendering.transform.EntityTransformAlgorithm;
import com.ladakx.inertia.api.rendering.transform.RenderTransformService;
import com.ladakx.inertia.core.impl.rendering.transform.MutableEntityTransformContext;
import com.ladakx.inertia.core.impl.rendering.transform.MutableEntityTransformImpl;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

final class RenderEntityImpl implements RenderEntity {

    private final NetworkEntityTracker tracker;
    private final RenderTransformService transformService;
    private final NetworkVisual visual;
    private final String modelId;
    private final String key;
    private final @Nullable String placeOnKey;

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
    private final MutableEntityTransformContext transformContext = new MutableEntityTransformContext();
    private final MutableEntityTransformImpl transformOutput = new MutableEntityTransformImpl();

    // Objects passed to tracker (mutated in-place to avoid allocations)
    private final Location trackerLocation;
    private final Quaternionf trackerRotation = new Quaternionf();

    RenderEntityImpl(@NotNull NetworkEntityTracker tracker,
                     @NotNull RenderTransformService transformService,
                     @NotNull NetworkVisual visual,
                     @NotNull String modelId,
                     @NotNull String key,
                     @NotNull Vector localOffset,
                     @NotNull Quaternionf localRotation,
                     boolean syncPosition,
                     boolean syncRotation,
                     @Nullable String placeOnKey) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.transformService = Objects.requireNonNull(transformService, "transformService");
        this.visual = Objects.requireNonNull(visual, "visual");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.key = Objects.requireNonNull(key, "key");
        this.placeOnKey = (placeOnKey == null || placeOnKey.isBlank()) ? null : placeOnKey;
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
        setBaseTransformFast(location, rotation);
        recompute();
    }

    void setBaseTransformFast(@NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("location.world is null");
        }
        basePos.set((float) location.getX(), (float) location.getY(), (float) location.getZ());
        baseRot.set(rotation);
        trackerLocation.setWorld(world);
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

    String key() {
        return key;
    }

    @Nullable String placeOnKey() {
        return placeOnKey;
    }

    void recomputeForSpawn() {
        if (closed) return;
        recompute();
    }

    private void recompute() {
        transformStack.getLocalTranslation(stackPos);
        transformStack.getLocalRotation(stackRot);

        localPos.set(defOffset);
        EntityTransformAlgorithm algorithm = transformService.resolveAlgorithm(modelId, key);
        transformContext.set(
                modelId,
                key,
                placeOnKey != null,
                placeOnKey != null,
                syncPosition,
                syncRotation,
                basePos,
                baseRot,
                localPos,
                defRotation,
                stackPos,
                stackRot
        );
        algorithm.compute(transformContext, transformOutput);

        trackerLocation.setX(transformOutput.position().x);
        trackerLocation.setY(transformOutput.position().y);
        trackerLocation.setZ(transformOutput.position().z);
        trackerRotation.set(transformOutput.rotation());
    }
}
