package com.ladakx.inertia.core.impl;

import com.ladakx.inertia.api.rendering.RenderingService;
import com.ladakx.inertia.api.rendering.VisualTracker;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import com.ladakx.inertia.rendering.RenderFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class RenderingServiceImpl implements RenderingService {

    private final RenderFactory renderFactory;
    private final VisualTracker visualTracker;

    public RenderingServiceImpl(@NotNull RenderFactory renderFactory, @NotNull NetworkEntityTracker tracker) {
        this.renderFactory = Objects.requireNonNull(renderFactory, "renderFactory");
        Objects.requireNonNull(tracker, "tracker");
        this.visualTracker = new VisualTracker() {
            @Override
            public void register(@NotNull com.ladakx.inertia.rendering.NetworkVisual visual,
                                 @NotNull org.bukkit.Location location,
                                 @NotNull org.joml.Quaternionf rotation) {
                tracker.register(visual, location, rotation);
            }

            @Override
            public void updateState(@NotNull com.ladakx.inertia.rendering.NetworkVisual visual,
                                    @NotNull org.bukkit.Location location,
                                    @NotNull org.joml.Quaternionf rotation) {
                tracker.updateState(visual, location, rotation);
            }

            @Override
            public void updateMetadata(@NotNull com.ladakx.inertia.rendering.NetworkVisual visual, boolean critical) {
                tracker.updateMetadata(visual, critical);
            }

            @Override
            public void unregister(@NotNull com.ladakx.inertia.rendering.NetworkVisual visual) {
                tracker.unregister(visual);
            }

            @Override
            public void unregisterById(int id) {
                tracker.unregisterById(id);
            }

            @Override
            public void unregisterBatch(@NotNull java.util.Collection<Integer> ids) {
                tracker.unregisterBatch(ids);
            }

            @Override
            public boolean isVisualClosed(int visualId) {
                return tracker.isVisualClosed(visualId);
            }
        };
    }

    @Override
    public @NotNull RenderFactory renderFactory() {
        return renderFactory;
    }

    @Override
    public @NotNull VisualTracker visualTracker() {
        return visualTracker;
    }
}
