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

    void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation);

    void updateMetadata(@NotNull NetworkVisual visual, boolean critical);

    void unregister(@NotNull NetworkVisual visual);

    void unregisterById(int id);

    void unregisterBatch(@NotNull Collection<Integer> ids);

    boolean isVisualClosed(int visualId);
}

