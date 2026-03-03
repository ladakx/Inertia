package com.ladakx.inertia.api.rendering.transform;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

/**
 * Well-known service keys for render transform integrations.
 */
public final class RenderingTransformServices {

    public static final @NotNull ServiceKey<RenderTransformService> TRANSFORMS =
            new ServiceKey<>("inertia.render.transforms", RenderTransformService.class);

    private RenderingTransformServices() {
    }
}

