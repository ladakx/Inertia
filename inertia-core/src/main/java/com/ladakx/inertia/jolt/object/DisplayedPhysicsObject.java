package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.ladakx.inertia.jolt.snapshot.VisualUpdate;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.render.runtime.PhysicsDisplayComposite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DisplayedPhysicsObject extends AbstractPhysicsObject {

    private boolean wasActive = true;

    public DisplayedPhysicsObject(@NotNull MinecraftSpace space, @NotNull BodyCreationSettings bodySettings) {
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
}