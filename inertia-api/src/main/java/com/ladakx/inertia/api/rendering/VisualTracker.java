package com.ladakx.inertia.api.rendering;

import com.ladakx.inertia.rendering.NetworkVisual;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.Collection;

/**
 * Tracker facade for registering/updating visuals.
 * <p>
 * Note: ticking/scheduling is managed by Inertia itself; external plugins should only
 * register/update/unregister visuals.
 */
public interface VisualTracker {

    void register(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation);

    /**
     * Registers a visual with an initial enabled state.
     * <p>
     * Enabled=false means the visual is kept tracked but not visible to players until enabled again.
     */
    default void register(@NotNull NetworkVisual visual,
                          @NotNull Location location,
                          @NotNull Quaternionf rotation,
                          boolean enabled) {
        register(visual, location, rotation);
        updateState(visual, location, rotation, enabled);
    }

    void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation);

    /**
     * Updates position/rotation and toggles the enabled state (hide/show without unregistering).
     */
    default void updateState(@NotNull NetworkVisual visual,
                             @NotNull Location location,
                             @NotNull Quaternionf rotation,
                             boolean enabled) {
        updateState(visual, location, rotation);
    }

    void updateMetadata(@NotNull NetworkVisual visual, boolean critical);

    void unregister(@NotNull NetworkVisual visual);

    void unregisterById(int id);

    void unregisterBatch(@NotNull Collection<Integer> ids);

    boolean isVisualClosed(int visualId);
}
