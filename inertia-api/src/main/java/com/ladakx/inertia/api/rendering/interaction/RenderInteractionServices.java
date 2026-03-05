package com.ladakx.inertia.api.rendering.interaction;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

public final class RenderInteractionServices {

    public static final @NotNull ServiceKey<RenderInteractionService> INTERACTION =
            new ServiceKey<>("inertia.rendering.interaction", RenderInteractionService.class);

    private RenderInteractionServices() {
    }
}
