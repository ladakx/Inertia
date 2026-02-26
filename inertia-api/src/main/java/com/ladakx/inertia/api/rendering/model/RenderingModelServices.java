package com.ladakx.inertia.api.rendering.model;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

/**
 * Well-known service keys for rendering model integrations.
 */
public final class RenderingModelServices {

    public static final @NotNull ServiceKey<RenderModelRegistryService> MODELS =
            new ServiceKey<>("inertia.render.models", RenderModelRegistryService.class);

    private RenderingModelServices() {
    }
}

