package com.ladakx.inertia.api.rendering;

import com.ladakx.inertia.rendering.RenderFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Public rendering access for other plugins.
 */
public interface RenderingService {

    @NotNull RenderFactory renderFactory();

    @NotNull VisualTracker visualTracker();
}

