package com.ladakx.inertia.rendering.config;

import com.ladakx.inertia.rendering.config.RenderModelDefinition;

import java.util.List;
import java.util.Objects;

public final class SingleRenderModelSelector implements RenderModelSelector {

    private final RenderModelVariant variant;

    public SingleRenderModelSelector(RenderModelDefinition model) {
        Objects.requireNonNull(model, "model");
        this.variant = new RenderModelVariant(null, model);
    }

    @Override
    public List<RenderModelVariant> variants() {
        return List.of(variant);
    }
}

