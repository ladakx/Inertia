package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.ladakx.inertia.jolt.snapshot.VisualUpdate;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.render.runtime.PhysicsDisplayComposite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class for physics objects that have a visual representation.
 * Implements Sleep State Machine logic for Snapshot generation.
 */
public abstract class DisplayedPhysicsObject extends AbstractPhysicsObject {

    private boolean wasActive = true;

    public DisplayedPhysicsObject(@NotNull MinecraftSpace space, @NotNull BodyCreationSettings bodySettings) {
        super(space, bodySettings);
    }

    /**
     * Повертає збережену Bukkit-сутність, пов'язану з цим фізичним тілом.
     */
    public abstract @Nullable PhysicsDisplayComposite getDisplay();

    /**
     * Called by the Physics Thread to generate visual updates for this frame.
     * Implements the Sleep State Machine optimization.
     *
     * @param accumulator List to add updates to.
     */
    public void captureSnapshot(List<VisualUpdate> accumulator) {
        PhysicsDisplayComposite display = getDisplay();
        if (display == null) return;

        boolean isActive = getBody().isActive();

        // State Machine Logic
        if (isActive) {
            // STATE: Active
            // Body is moving. Calculate full interpolation.
            accumulator.addAll(display.capture(false));
            wasActive = true;

        } else if (wasActive) {
            // STATE: Just Fell Asleep
            // Body just stopped. Send ONE last packet to "cement" the position.
            accumulator.addAll(display.capture(true));
            wasActive = false; // Transition to deep sleep next frame

        } else {
            // STATE: Sleeping (Deep Sleep)
            // Body is inactive and was inactive last frame.
            // Do NOT calculate math. Do NOT send packets.
            // Zero CPU usage.
        }
    }

    // Deprecated: The old direct update method is no longer used by the Snapshot system.
    // We keep it empty or throw exception to ensure it's not called.
    public void update() {
        // No-op in new architecture
    }
}