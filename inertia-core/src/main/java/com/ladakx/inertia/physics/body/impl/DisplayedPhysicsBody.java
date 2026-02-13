package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.world.snapshot.VisualState;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.rendering.runtime.PhysicsDisplayComposite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DisplayedPhysicsBody extends AbstractPhysicsBody {

    protected final RenderFactory renderFactory;
    protected final PhysicsBodyRegistry modelRegistry;
    protected PhysicsDisplayComposite displayComposite;
    private boolean wasActive = true;

    public DisplayedPhysicsBody(@NotNull PhysicsWorld space,
                                @NotNull BodyCreationSettings bodySettings,
                                @NotNull RenderFactory renderFactory,
                                @NotNull PhysicsBodyRegistry modelRegistry) {
        super(space, bodySettings);
        this.renderFactory = renderFactory;
        this.modelRegistry = modelRegistry;
    }

    public @Nullable PhysicsDisplayComposite getDisplay() {
        return displayComposite;
    }

    public void captureSnapshot(java.util.List<com.ladakx.inertia.physics.world.snapshot.VisualState> accumulator, com.ladakx.inertia.physics.world.snapshot.SnapshotPool pool, com.github.stephengold.joltjni.RVec3 origin) {
        if (displayComposite == null) return;

        boolean isActive = getBody().isActive();
        if (isActive) {
            displayComposite.capture(false, origin, accumulator, pool);
            wasActive = true;
        } else if (wasActive) {
            displayComposite.capture(true, origin, accumulator, pool);
            wasActive = false;
        }
    }

    public boolean isDisplayCaptureRequired() {
        return displayComposite != null && (getBody().isActive() || wasActive);
    }

    public void captureSnapshotActive(List<VisualState> accumulator,
                                      SnapshotPool pool,
                                      com.github.stephengold.joltjni.RVec3 origin,
                                      float positionX,
                                      float positionY,
                                      float positionZ,
                                      float rotationX,
                                      float rotationY,
                                      float rotationZ,
                                      float rotationW) {
        if (displayComposite == null) {
            return;
        }
        java.util.Objects.requireNonNull(accumulator, "accumulator");
        java.util.Objects.requireNonNull(pool, "pool");
        java.util.Objects.requireNonNull(origin, "origin");
        displayComposite.captureWithTransform(
                false,
                origin,
                accumulator,
                pool,
                positionX,
                positionY,
                positionZ,
                rotationX,
                rotationY,
                rotationZ,
                rotationW
        );
        wasActive = true;
    }

    public void captureSnapshotSleeping(List<VisualState> accumulator,
                                        SnapshotPool pool,
                                        com.github.stephengold.joltjni.RVec3 origin) {
        if (displayComposite == null || !wasActive) {
            return;
        }
        java.util.Objects.requireNonNull(accumulator, "accumulator");
        java.util.Objects.requireNonNull(pool, "pool");
        java.util.Objects.requireNonNull(origin, "origin");
        displayComposite.capture(true, origin, accumulator, pool);
        wasActive = false;
    }

    public void freeze(@Nullable java.util.UUID clusterId) {
        if (!isValid()) return;
        if (displayComposite != null) {
            displayComposite.markAsStatic(clusterId);
        }
        super.destroy();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (displayComposite != null) {
            displayComposite.destroy();
        }
    }

    public void checkAndRestoreVisuals() {
        if (displayComposite != null && !displayComposite.isValid()) {
            displayComposite = recreateDisplay();
        }
    }

    protected abstract PhysicsDisplayComposite recreateDisplay();
}
