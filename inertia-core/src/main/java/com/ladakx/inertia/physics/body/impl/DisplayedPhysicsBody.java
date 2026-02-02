package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.world.snapshot.VisualUpdate;
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

    public void captureSnapshot(List<VisualUpdate> accumulator) {
        if (displayComposite == null) return;

        // Pass World Origin to display composite for correct rendering position
        var origin = getSpace().getOrigin();

        boolean isActive = getBody().isActive();
        if (isActive) {
            accumulator.addAll(displayComposite.capture(false, origin));
            wasActive = true;
        } else if (wasActive) {
            accumulator.addAll(displayComposite.capture(true, origin));
            wasActive = false;
        }
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