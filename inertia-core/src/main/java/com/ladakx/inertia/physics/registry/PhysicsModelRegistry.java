package com.ladakx.inertia.physics.registry;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.files.config.BodiesConfig;
import com.ladakx.inertia.files.config.RenderConfig;
import com.ladakx.inertia.physics.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.config.BodyDefinition;
import com.ladakx.inertia.physics.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.config.RagdollDefinition;
import com.ladakx.inertia.render.config.RenderModelDefinition;

import java.util.*;

/**
 * Реєстр bodyId → (physics definition + optional render model).
 * Адаптовано під поліморфну структуру BodyDefinition.
 */
public final class PhysicsModelRegistry {

    /**
     * Контейнер для пари: фізичне визначення та (опціонально) модель відображення.
     * Примітка: Для Ragdoll renderModel буде порожнім, оскільки вони мають множину моделей.
     */
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
            Optional<RenderModelDefinition> renderOpt = Optional.empty();

            // Логіка резолвінгу рендер-моделі залежить від типу тіла
            if (body instanceof BlockBodyDefinition blockDef) {
                renderOpt = resolveRender(blockDef.id(), blockDef.renderModel(), renderConfig);
            } else if (body instanceof ChainBodyDefinition chainDef) {
                renderOpt = resolveRender(chainDef.id(), chainDef.renderModel(), renderConfig);
            } else if (body instanceof RagdollDefinition) {
                // Ragdoll має складну структуру render (map), тому тут ми не кешуємо одну модель.
                // Спавнер Ragdoll-ів повинен сам запитувати RenderConfig.
                // Log debug if needed: InertiaLogger.debug("Skipping single render resolve for Ragdoll: " + body.id());
            }

            BodyModel model = new BodyModel(body, renderOpt);
            newMap.put(body.id(), model);
        }

        this.models = Map.copyOf(newMap);
        InertiaLogger.info("PhysicsModelRegistry reloaded: " + models.size() + " body definitions");
    }

    private Optional<RenderModelDefinition> resolveRender(String bodyId, String renderId, RenderConfig renderConfig) {
        if (renderId == null || renderId.isEmpty()) {
            return Optional.empty();
        }
        Optional<RenderModelDefinition> opt = renderConfig.find(renderId);
        if (opt.isEmpty()) {
            InertiaLogger.warn("Body '" + bodyId + "' references missing render model '" + renderId + "'");
        }
        return opt;
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