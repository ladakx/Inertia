package com.ladakx.inertia.configuration.dto;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.config.RenderEntitySettingsValidator;
import com.ladakx.inertia.rendering.config.enums.InertiaBillboard;
import com.ladakx.inertia.rendering.config.enums.InertiaDisplayMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.*;

public final class RenderConfig {

    private final Map<String, RenderModelDefinition> models;

    public RenderConfig(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        this.models = Collections.unmodifiableMap(parse(config));
    }

    private Map<String, RenderModelDefinition> parse(FileConfiguration config) {
        Map<String, RenderModelDefinition> result = new LinkedHashMap<>();

        for (String modelId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(modelId);
            if (section == null) continue;

            boolean syncPos = section.getBoolean("sync.position", true);
            boolean syncRot = section.getBoolean("sync.rotation", true);

            ConfigurationSection entitiesSec = section.getConfigurationSection("entities");
            if (entitiesSec == null) {
                InertiaLogger.warn("Render model '" + modelId + "' has no 'entities' section");
                continue;
            }

            Map<String, RenderEntityDefinition> entities = new LinkedHashMap<>();

            for (String entityKey : entitiesSec.getKeys(false)) {
                ConfigurationSection eSec = entitiesSec.getConfigurationSection(entityKey);
                if (eSec == null) continue;

                RenderEntityDefinition def = parseEntity(modelId, entityKey, eSec);
                if (def != null) {
                    entities.put(entityKey, def);
                }
            }

            if (result.put(modelId, new RenderModelDefinition(modelId, syncPos, syncRot, entities)) != null) {
                InertiaLogger.warn("Duplicate render model id '" + modelId + "', overriding");
            }
        }
        return result;
    }

    private RenderEntityDefinition parseEntity(String modelId, String key, ConfigurationSection section) {
        String typeRaw = section.getString("type");
        if (typeRaw == null) return null;

        RenderEntityDefinition.EntityKind kind;
        try {
            kind = RenderEntityDefinition.EntityKind.valueOf(typeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            InertiaLogger.warn("Invalid entity type: " + typeRaw);
            return null;
        }

        String itemModelKey = section.getString("item-model");

        // Material handling needs care on 1.16.5 if config uses 1.21 block names
        Material blockType = null;
        if (section.isString("block")) {
            String matName = section.getString("block");
            try {
                blockType = Material.matchMaterial(matName);
                if (blockType == null && !matName.isEmpty()) {
                    // Якщо ми на 1.16.5, а в конфігу блок з 1.21 - це не помилка, просто ми його не знайдемо
                    // Можна додати лог або ігнорувати
                }
            } catch (Exception ignored) {}
        }

        InertiaDisplayMode displayMode = InertiaDisplayMode.parse(section.getString("display-mode"));

        Vector localOffset = parseVector(section.getString("local-offset"), new Vector(0, 0, 0));
        Quaternionf localRotation = parseRotation(section.getString("local-rotation"));
        Vector scale = parseVector(section.getString("scale"), new Vector(1, 1, 1));
        Vector translation = parseVector(section.getString("translation"), new Vector(0, 0, 0));

        boolean showWhenActive = section.getBoolean("show-when.active", true);
        boolean showWhenSleeping = section.getBoolean("show-when.sleeping", true);

        // Parse nullable floats/ints safely
        Float viewRange = section.contains("view-range") ? (float) section.getDouble("view-range") : null;
        Float shadowRadius = section.contains("shadow-radius") ? (float) section.getDouble("shadow-radius") : null;
        Float shadowStrength = section.contains("shadow-strength") ? (float) section.getDouble("shadow-strength") : null;
        Integer interpolationDuration = section.contains("interpolation-duration") ? section.getInt("interpolation-duration") : null;
        Integer teleportDuration = section.contains("teleport-duration") ? section.getInt("teleport-duration") : null;

        InertiaBillboard billboard = InertiaBillboard.parse(section.getString("billboard"));

        Integer brightnessBlock = null, brightnessSky = null;
        if (section.contains("brightness.block")) brightnessBlock = section.getInt("brightness.block");
        if (section.contains("brightness.sky")) brightnessSky = section.getInt("brightness.sky");

        boolean rotateTranslation = section.getBoolean("rotate-translation", true);

        Map<String, Object> settings = parseSettings(section.getConfigurationSection("settings"));
        RenderEntitySettingsValidator.validate(modelId, key, kind, settings);

        return new RenderEntityDefinition(
                key, kind, itemModelKey, blockType, displayMode,
                localOffset, localRotation, scale, translation,
                showWhenActive, showWhenSleeping, rotateTranslation, viewRange, shadowRadius, shadowStrength,
                interpolationDuration, teleportDuration, billboard, brightnessBlock, brightnessSky,
                section.getBoolean("small", false), section.getBoolean("invisible", true),
                section.getBoolean("marker", true), section.getBoolean("base-plate", false),
                section.getBoolean("arms", false),
                settings
        );
    }

    private Map<String, Object> parseSettings(ConfigurationSection section) {
        if (section == null) return Collections.emptyMap();
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            out.put(key, deepConvert(value));
        }
        return out;
    }

    private Object deepConvert(Object value) {
        if (value == null) return null;
        if (value instanceof ConfigurationSection cs) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (String k : cs.getKeys(false)) {
                nested.put(k, deepConvert(cs.get(k)));
            }
            return nested;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object k = e.getKey();
                if (k == null) continue;
                nested.put(String.valueOf(k), deepConvert(e.getValue()));
            }
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object v : list) copy.add(deepConvert(v));
            return copy;
        }
        return value;
    }

    private Vector parseVector(String raw, Vector def) {
        if (raw == null || raw.isBlank()) return def;
        try {
            String[] parts = raw.replace(',', ' ').trim().split("\\s+");
            if (parts.length == 3) {
                return new Vector(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            }
        } catch (NumberFormatException ignored) {}
        return def;
    }

    private Quaternionf parseRotation(String raw) {
        if (raw == null || raw.isBlank()) return new Quaternionf();
        Vector v = parseVector(raw, new Vector(0, 0, 0));
        return new Quaternionf().rotationXYZ((float) v.getX(), (float) v.getY(), (float) v.getZ());
    }

    // Getters...
    public Optional<RenderModelDefinition> find(String id) { return Optional.ofNullable(models.get(id)); }
    public RenderModelDefinition require(String id) {
        if (!models.containsKey(id)) throw new IllegalArgumentException("Unknown model: " + id);
        return models.get(id);
    }
}
