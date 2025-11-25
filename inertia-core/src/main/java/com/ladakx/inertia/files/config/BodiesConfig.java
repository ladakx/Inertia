package com.ladakx.inertia.files.config;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.physics.config.BodyDefinition;
import com.ladakx.inertia.physics.config.BodyPhysicsSettings;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Immutable-конфіг для bodies.yml.
 *
 * Формат (приклад):
 *
 * blocks:
 *   stone:
 *     mass: 1.0
 *     friction: 0.8
 *     restitution: 0.0
 *     linear-damping: 0.1
 *     angular-damping: 0.1
 *     motion-type: Dynamic
 *     object-layer: 0
 *     render-model: stone
 *     shape:
 *       - "type=box x=0.5 y=0.5 z=0.5"
 */
public final class BodiesConfig {

    private final Map<String, BodyDefinition> bodies;

    public BodiesConfig(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        this.bodies = Collections.unmodifiableMap(parse(config));
    }

    private Map<String, BodyDefinition> parse(FileConfiguration config) {
        Map<String, BodyDefinition> result = new LinkedHashMap<>();

        for (String rootKey : config.getKeys(false)) {
            ConfigurationSection rootSection = config.getConfigurationSection(rootKey);
            if (rootSection == null) {
                continue;
            }

            if (isBodySection(rootSection)) {
                parseBodySection(result, null, rootKey, rootSection);
            } else {
                for (String childKey : rootSection.getKeys(false)) {
                    ConfigurationSection bodySection = rootSection.getConfigurationSection(childKey);
                    if (bodySection == null) {
                        continue;
                    }
                    parseBodySection(result, rootKey, childKey, bodySection);
                }
            }
        }

        return result;
    }

    private boolean isBodySection(ConfigurationSection section) {
        return section.contains("shape")
                || section.contains("mass")
                || section.contains("render-model");
    }

    private void parseBodySection(Map<String, BodyDefinition> result,
                                  String prefix,
                                  String localKey,
                                  ConfigurationSection section) {
        String id = (prefix == null || prefix.isEmpty())
                ? localKey
                : prefix + "." + localKey;

        List<String> shapeLines = section.getStringList("shape");
        if (shapeLines.isEmpty()) {
            InertiaLogger.warn("Body '" + id + "' has no 'shape' section in bodies.yml");
        }

        float mass = (float) section.getDouble("mass", 1.0d);
        if (mass <= 0f) {
            InertiaLogger.warn("Body '" + id + "' has non-positive mass: " + mass + ", clamping to 0.001");
            mass = 0.001f;
        }

        float friction = (float) section.getDouble("friction", 0.5d);
        if (friction < 0f) {
            InertiaLogger.warn("Body '" + id + "' has negative friction: " + friction + ", clamping to 0");
            friction = 0f;
        }

        float restitution = (float) section.getDouble("restitution", 0.0d);
        if (restitution < 0f) {
            InertiaLogger.warn("Body '" + id + "' has negative restitution: " + restitution + ", clamping to 0");
            restitution = 0f;
        }

        float linearDamping = (float) section.getDouble("linear-damping", 0.05d);
        float angularDamping = (float) section.getDouble("angular-damping", 0.05d);

        String motionTypeName = section.getString("motion-type", "Dynamic");
        EMotionType motionType;
        try {
            motionType = EMotionType.valueOf(motionTypeName);
        } catch (IllegalArgumentException ex) {
            InertiaLogger.warn("Body '" + id + "' has invalid motion-type: " + motionTypeName
                    + ", falling back to Dynamic");
            motionType = EMotionType.Dynamic;
        }

        int objectLayer = section.getInt("object-layer", 0);

        String renderModelId = section.getString("render-model", id);

        BodyPhysicsSettings physics = new BodyPhysicsSettings(
                mass,
                friction,
                restitution,
                linearDamping,
                angularDamping,
                motionType,
                objectLayer
        );

        BodyDefinition definition = new BodyDefinition(
                id,
                physics,
                List.copyOf(shapeLines),
                renderModelId
        );

        if (result.put(id, definition) != null) {
            InertiaLogger.warn("Duplicate body id '" + id + "' in bodies.yml, overriding previous definition");
        }
    }

    public Optional<BodyDefinition> find(String id) {
        return Optional.ofNullable(bodies.get(id));
    }

    public BodyDefinition require(String id) {
        BodyDefinition def = bodies.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown body id: " + id);
        }
        return def;
    }

    public Collection<BodyDefinition> all() {
        return bodies.values();
    }
}