package com.ladakx.inertia.files.config;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.render.config.RenderEntityDefinition;
import com.ladakx.inertia.render.config.RenderModelDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.*;

/**
 * Immutable-конфіг для render.yml.
 *
 * Формат відповідає прикладам з наданого файлу render.yml.
 */
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
            if (section == null) {
                continue;
            }

            boolean syncPos = section.getConfigurationSection("sync") == null
                    || section.getBoolean("sync.position", true);
            boolean syncRot = section.getConfigurationSection("sync") == null
                    || section.getBoolean("sync.rotation", true);

            ConfigurationSection entitiesSec = section.getConfigurationSection("entities");
            if (entitiesSec == null) {
                InertiaLogger.warn("Render model '" + modelId + "' has no 'entities' section in render.yml");
                continue;
            }

            Map<String, RenderEntityDefinition> entities = new LinkedHashMap<>();

            for (String entityKey : entitiesSec.getKeys(false)) {
                ConfigurationSection eSec = entitiesSec.getConfigurationSection(entityKey);
                if (eSec == null) {
                    continue;
                }

                RenderEntityDefinition def = parseEntity(modelId, entityKey, eSec);
                if (def != null) {
                    entities.put(entityKey, def);
                }
            }

            if (entities.isEmpty()) {
                InertiaLogger.warn("Render model '" + modelId + "' has empty 'entities' section");
            }

            RenderModelDefinition modelDefinition =
                    new RenderModelDefinition(modelId, syncPos, syncRot, entities);

            if (result.put(modelId, modelDefinition) != null) {
                InertiaLogger.warn("Duplicate render model id '" + modelId + "' in render.yml, overriding");
            }
        }

        return result;
    }

    private RenderEntityDefinition parseEntity(String modelId,
                                               String key,
                                               ConfigurationSection section) {
        String typeRaw = section.getString("type");
        if (typeRaw == null) {
            InertiaLogger.warn("Render model '" + modelId + "', entity '" + key + "' has no 'type'");
            return null;
        }

        RenderEntityDefinition.EntityKind kind;
        try {
            kind = RenderEntityDefinition.EntityKind.valueOf(typeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            InertiaLogger.warn("Render model '" + modelId + "', entity '" + key
                    + "' has invalid type: " + typeRaw);
            return null;
        }

        String itemModelKey = section.getString("item-model");
        Material blockType = null;
        if (section.isString("block")) {
            String matName = section.getString("block");
            try {
                blockType = Material.valueOf(matName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                InertiaLogger.warn("Render model '" + modelId + "', entity '" + key
                        + "' has invalid block material: " + matName);
            }
        }

        String displayModeRaw = section.getString("display-mode");

        Vector localOffset = parseVector(section.getString("local-offset"), new Vector(0, 0, 0));
        Quaternionf localRotation = parseRotation(section.getString("local-rotation"));
        Vector scale = parseVector(section.getString("scale"), new Vector(1, 1, 1));
        Vector translation = parseVector(section.getString("translation"), new Vector(0, 0, 0));

        boolean showWhenActive = section.getConfigurationSection("show-when") == null
                || section.getBoolean("show-when.active", true);
        boolean showWhenSleeping = section.getConfigurationSection("show-when") == null
                || section.getBoolean("show-when.sleeping", true);

        Float viewRange = section.isInt("view-range")
                ? (float) section.getInt("view-range")
                : null;
        Float shadowRadius = section.isInt("shadow-radius")
                ? (float) section.getInt("shadow-radius")
                : null;
        Float shadowStrength = section.isInt("shadow-strength")
                ? (float) section.getInt("shadow-strength")
                : null;
        Integer interpolationDuration = section.isInt("interpolation-duration")
                ? section.getInt("interpolation-duration")
                : null;
        Integer teleportDuration = section.isInt("teleport-duration")
                ? section.getInt("teleport-duration")
                : null;

        Display.Billboard billboard = null;
        String billboardRaw = section.getString("billboard");
        if (billboardRaw != null) {
            try {
                billboard = Display.Billboard.valueOf(billboardRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                InertiaLogger.warn("Render model '" + modelId + "', entity '" + key
                        + "' has invalid billboard: " + billboardRaw);
            }
        }

        Integer brightnessBlock = null;
        Integer brightnessSky = null;
        ConfigurationSection brightnessSec = section.getConfigurationSection("brightness");
        if (brightnessSec != null) {
            if (brightnessSec.isInt("block")) {
                brightnessBlock = brightnessSec.getInt("block");
            }
            if (brightnessSec.isInt("sky")) {
                brightnessSky = brightnessSec.getInt("sky");
            }
        }

        boolean small = section.getBoolean("small", false);
        boolean invisible = section.getBoolean("invisible", false);
        boolean marker = section.getBoolean("marker", false);
        boolean basePlate = section.getBoolean("base-plate", true);
        boolean arms = section.getBoolean("arms", false);

        return new RenderEntityDefinition(
                key,
                kind,
                itemModelKey,
                blockType,
                displayModeRaw,
                localOffset,
                localRotation,
                scale,
                translation,
                showWhenActive,
                showWhenSleeping,
                viewRange,
                shadowRadius,
                shadowStrength,
                interpolationDuration,
                teleportDuration,
                billboard,
                brightnessBlock,
                brightnessSky,
                small,
                invisible,
                marker,
                basePlate,
                arms
        );
    }

    private Vector parseVector(String raw, Vector def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        String cleaned = raw.replace(',', ' ').trim();
        String[] parts = cleaned.split("\\s+");
        if (parts.length != 3) {
            InertiaLogger.warn("Invalid vector '" + raw + "', expected 3 components, using default " + def);
            return def;
        }
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            return new Vector(x, y, z);
        } catch (NumberFormatException ex) {
            InertiaLogger.warn("Invalid vector '" + raw + "': " + ex.getMessage() + ", using default " + def);
            return def;
        }
    }

    /**
     * Локальна ротація вказана як Euler XYZ в радіанах.
     */
    private Quaternionf parseRotation(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Quaternionf(); // identity
        }
        Vector v = parseVector(raw, new Vector(0, 0, 0));
        return new Quaternionf().rotationXYZ(
                (float) v.getX(),
                (float) v.getY(),
                (float) v.getZ()
        );
    }

    public Optional<RenderModelDefinition> find(String id) {
        return Optional.ofNullable(models.get(id));
    }

    public RenderModelDefinition require(String id) {
        RenderModelDefinition def = models.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown render model id: " + id);
        }
        return def;
    }

    public Collection<RenderModelDefinition> all() {
        return models.values();
    }
}