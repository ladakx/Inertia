package com.ladakx.inertia.render.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Одна модель з render.yml — набір локальних сутностей + флаги sync.
 */
public record RenderModelDefinition(
        String id,
        boolean syncPosition,
        boolean syncRotation,
        Map<String, RenderEntityDefinition> entities
) {
    public RenderModelDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entities, "entities");
        entities = Collections.unmodifiableMap(new LinkedHashMap<>(entities));
    }

    public RenderEntityDefinition entity(String key) {
        return entities.get(key);
    }
}