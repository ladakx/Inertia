package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.ladakx.inertia.physics.world.snapshot.VisualUpdate;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.runtime.PhysicsDisplayComposite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DisplayedPhysicsBody extends AbstractPhysicsBody {

    private boolean wasActive = true;

    public DisplayedPhysicsBody(@NotNull PhysicsWorld space, @NotNull BodyCreationSettings bodySettings) {
        super(space, bodySettings);
    }

    public abstract @Nullable PhysicsDisplayComposite getDisplay();

    public void captureSnapshot(List<VisualUpdate> accumulator) {
        PhysicsDisplayComposite display = getDisplay();
        if (display == null) return;

        boolean isActive = getBody().isActive();

        if (isActive) {
            accumulator.addAll(display.capture(false));
            wasActive = true;
        } else if (wasActive) {
            accumulator.addAll(display.capture(true));
            wasActive = false;
        }
    }

    public void freeze(@Nullable java.util.UUID clusterId) {
        if (!isValid()) return;

        com.ladakx.inertia.rendering.runtime.PhysicsDisplayComposite display = getDisplay();
        if (display != null) {
            display.markAsStatic(clusterId);
        }

        super.destroy();
    }
}