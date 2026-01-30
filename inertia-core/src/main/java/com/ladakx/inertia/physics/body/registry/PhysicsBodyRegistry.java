package com.ladakx.inertia.physics.body.registry;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.BodiesConfig;
import com.ladakx.inertia.configuration.dto.RenderConfig;
import com.ladakx.inertia.physics.body.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.body.config.BodyDefinition;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.config.RagdollDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;

import java.util.*;

/**
 * Реєстр bodyId → (physics definition + optional render model).
 * Зберігає зв'язок між фізичним описом тіла та його візуальною моделлю (або частинами).
 */
public final class PhysicsBodyRegistry {

    /**
     * Контейнер для пари: фізичне визначення та (опціонально) модель відображення.
     */
    public record BodyModel(
            BodyDefinition bodyDefinition,
            // Для простих об'єктів (BLOCK, CHAIN) - одна модель
            Optional<RenderModelDefinition> renderModel,
            // Для складених об'єктів (RAGDOLL) - мапа частин (назва частини -> модель)
            Map<String, RenderModelDefinition> parts
    ) {
        public BodyModel {
            Objects.requireNonNull(bodyDefinition, "bodyDefinition cannot be null");
            Objects.requireNonNull(renderModel, "renderModel cannot be null");
            parts = parts != null ? Map.copyOf(parts) : Map.of();
        }

        /**
         * Зручний конструктор для простих тіл.
         */
        public BodyModel(BodyDefinition bodyDefinition, Optional<RenderModelDefinition> renderModel) {
            this(bodyDefinition, renderModel, Map.of());
        }
    }

    private volatile Map<String, BodyModel> models = Map.of();

    public void reload(BodiesConfig bodiesConfig, RenderConfig renderConfig) {
        Objects.requireNonNull(bodiesConfig, "bodiesConfig");
        Objects.requireNonNull(renderConfig, "renderConfig");

        Map<String, BodyModel> newMap = new LinkedHashMap<>();

        for (BodyDefinition body : bodiesConfig.all()) {
            Optional<RenderModelDefinition> renderOpt = Optional.empty();
            Map<String, RenderModelDefinition> partsMap = new HashMap<>();

            // Логіка резолвінгу рендер-моделі залежить від типу тіла
            if (body instanceof BlockBodyDefinition blockDef) {
                renderOpt = resolveRender(blockDef.id(), blockDef.renderModel(), renderConfig);
            } else if (body instanceof ChainBodyDefinition chainDef) {
                renderOpt = resolveRender(chainDef.id(), chainDef.renderModel(), renderConfig);
            } else if (body instanceof RagdollDefinition ragdollDef) {
                // Для Ragdoll перебираємо всі частини, визначені в конфігу
                for (Map.Entry<String, RagdollDefinition.RagdollPartDefinition> entry : ragdollDef.parts().entrySet()) {
                    String partName = entry.getKey();
                    String renderModelId = entry.getValue().renderModelId();

                    Optional<RenderModelDefinition> partRender = renderConfig.find(renderModelId);
                    if (partRender.isPresent()) {
                        partsMap.put(partName, partRender.get());
                    } else {
                        InertiaLogger.warn("Ragdoll '" + body.id() + "' part '" + partName + "' references missing render model '" + renderModelId + "'");
                    }
                }
            }

            BodyModel model = new BodyModel(body, renderOpt, partsMap);
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