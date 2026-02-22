package com.ladakx.inertia.rendering.config;

import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.version.ClientVersionRange;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record RenderModelVariant(
        @Nullable ClientVersionRange clientRange,
        RenderModelDefinition model
) {
    public RenderModelVariant {
        Objects.requireNonNull(model, "model");
    }
}

