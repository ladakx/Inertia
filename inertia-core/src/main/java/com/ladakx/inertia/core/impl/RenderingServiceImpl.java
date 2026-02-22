package com.ladakx.inertia.core.impl;

import com.ladakx.inertia.api.rendering.RenderingService;
import com.ladakx.inertia.api.rendering.VisualTracker;
import com.ladakx.inertia.api.rendering.entity.RenderEntityService;
import com.ladakx.inertia.core.impl.rendering.RenderEntityServiceImpl;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import com.ladakx.inertia.rendering.RenderFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class RenderingServiceImpl implements RenderingService {

    private final RenderFactory renderFactory;
    private final VisualTracker visualTracker;
    private final RenderEntityService entityService;

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
            public void register(@NotNull com.ladakx.inertia.rendering.NetworkVisual visual,
                                 @NotNull org.bukkit.Location location,
                                 @NotNull org.joml.Quaternionf rotation,
                                 boolean enabled) {
                tracker.register(visual, location, rotation, null, 0x07, enabled);
            }

            @Override
            public void updateState(@NotNull com.ladakx.inertia.rendering.NetworkVisual visual,
                                    @NotNull org.bukkit.Location location,
                                    @NotNull org.joml.Quaternionf rotation) {
                tracker.updateState(visual, location, rotation);
            }

            @Override
            public void updateState(@NotNull com.ladakx.inertia.rendering.NetworkVisual visual,
                                    @NotNull org.bukkit.Location location,
                                    @NotNull org.joml.Quaternionf rotation,
                                    boolean enabled) {
                tracker.updateState(visual, location, rotation, enabled);
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
        this.entityService = new RenderEntityServiceImpl(renderFactory, tracker);
    }

    @Override
    public @NotNull RenderFactory renderFactory() {
        return renderFactory;
    }

    @Override
    public @NotNull VisualTracker visualTracker() {
        return visualTracker;
    }

    @Override
    public @NotNull RenderEntityService entities() {
        return entityService;
    }
}
