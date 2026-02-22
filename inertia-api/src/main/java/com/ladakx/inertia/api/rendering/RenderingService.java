package com.ladakx.inertia.api.rendering;

import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.api.rendering.entity.RenderEntityService;
import org.jetbrains.annotations.NotNull;

/**
 * Public rendering access for other plugins.
 */
public interface RenderingService {

    @NotNull RenderFactory renderFactory();

    @NotNull VisualTracker visualTracker();

    /**
     * High-level API for creating and managing render entities/models with transform composition.
     */
    @NotNull RenderEntityService entities();
}
