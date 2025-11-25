package com.ladakx.inertia.physics.registry;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.files.config.BodiesConfig;
import com.ladakx.inertia.files.config.RenderConfig;
import com.ladakx.inertia.physics.config.BodyDefinition;
import com.ladakx.inertia.render.config.RenderModelDefinition;

import java.util.*;

/**
 * Реєстр bodyId → (physics + optional render model).
 * Використовує volatile-мапу для атомарного reload.
 */
public final class PhysicsModelRegistry {

    public record BodyModel(
            BodyDefinition bodyDefinition,
            Optional<RenderModelDefinition> renderModel
    ) {
    }

    private volatile Map<String, BodyModel> models = Map.of();

    public void reload(BodiesConfig bodiesConfig, RenderConfig renderConfig) {
        Objects.requireNonNull(bodiesConfig, "bodiesConfig");
        Objects.requireNonNull(renderConfig, "renderConfig");

        Map<String, BodyModel> newMap = new LinkedHashMap<>();

        for (BodyDefinition body : bodiesConfig.all()) {
            String renderId = body.renderModel();
            Optional<RenderModelDefinition> renderOpt = renderConfig.find(renderId);
            if (renderOpt.isEmpty()) {
                InertiaLogger.warn("Body '" + body.id()
                        + "' references missing render model '" + renderId + "'");
            }
            BodyModel model = new BodyModel(body, renderOpt);
            newMap.put(body.id(), model);
        }

        this.models = Map.copyOf(newMap);
        InertiaLogger.info("PhysicsModelRegistry reloaded: " + models.size() + " body definitions");
    }

    public Optional<BodyModel> find(String bodyId) {
        return Optional.ofNullable(models.get(bodyId));
    }

    public BodyModel require(String bodyId) {
        BodyModel model = models.get(bodyId);
        if (model == null) {
            throw new IllegalArgumentException("Unknown bodyId: " + bodyId);
        }
        return model;
    }

    public Collection<BodyModel> all() {
        return models.values();
    }
}