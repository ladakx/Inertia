package com.ladakx.inertia.files.config;

import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.physics.config.BodyDefinition;
import com.ladakx.inertia.physics.config.BodyPhysicsSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Immutable-конфіг для bodies.yml.
 */
public final class BodiesConfig {

    private final Map<String, BodyDefinition> bodies;

    public BodiesConfig(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        this.bodies = Collections.unmodifiableMap(parse(config));
    }

    private Map<String, BodyDefinition> parse(FileConfiguration config) {
        Map<String, BodyDefinition> result = new LinkedHashMap<>();

        // Обробляємо основні категорії, які є поодинокими фізичними тілами
        parseCategory(config, "blocks", result);
        parseCategory(config, "chains", result);

        // Примітка: Ragdolls мають складну структуру (joints, multiple shapes),
        // тому вони не можуть бути збережені як простий BodyDefinition.
        // Їх слід обробляти окремим методом або класом (наприклад, RagdollConfig).
        if (config.contains("ragdolls")) {
            InertiaLogger.info("Detected 'ragdolls' section. These require a separate loader definition.");
        }

        return result;
    }

    private void parseCategory(FileConfiguration config, String category, Map<String, BodyDefinition> result) {
        ConfigurationSection categorySection = config.getConfigurationSection(category);
        if (categorySection == null) {
            return;
        }

        for (String key : categorySection.getKeys(false)) {
            ConfigurationSection bodySection = categorySection.getConfigurationSection(key);
            if (bodySection == null) continue;

            // Формуємо повний ID (наприклад, "blocks.stone" або просто "stone", залежно від вашої логіки)
            // У попередньому коді ви використовували префікси, тут залишимо просто ключ, якщо імена унікальні,
            // або category + "." + key, якщо хочете розділяти їх.
            String fullId = category + "." + key;

            try {
                BodyDefinition def = parseSingleBody(fullId, bodySection);
                if (result.put(fullId, def) != null) {
                    InertiaLogger.warn("Duplicate body id '" + fullId + "' in bodies.yml, overriding.");
                }
            } catch (Exception e) {
                InertiaLogger.error("Failed to parse body '" + fullId + "': " + e.getMessage());
            }
        }
    }

    private BodyDefinition parseSingleBody(String id, ConfigurationSection section) {
        // --- 1. Render Section ---
        // Якщо модель не вказана, використовуємо ID тіла як fallback
        String renderModelId = section.getString("render.model", id);

        // --- 2. Physics Section ---
        ConfigurationSection physSection = section.getConfigurationSection("physics");
        if (physSection == null) {
            InertiaLogger.warn("Body '" + id + "' has no 'physics' section. Using defaults.");
            // Можна створити пусту секцію, щоб уникнути NullPointerException, або викинути помилку
            physSection = section.createSection("physics");
        }

        List<String> shapeLines = physSection.getStringList("shape");
        if (shapeLines.isEmpty()) {
            InertiaLogger.warn("Body '" + id + "' physics has no 'shape' defined!");
        }

        float mass = (float) physSection.getDouble("mass", 1.0d);
        if (mass <= 0f) {
            InertiaLogger.warn("Body '" + id + "' has non-positive mass: " + mass + ", clamping to 0.001");
            mass = 0.001f;
        }

        float friction = (float) physSection.getDouble("friction", 0.5d);
        if (friction < 0f) {
            InertiaLogger.warn("Body '" + id + "' has negative friction: " + friction + ", clamping to 0");
            friction = 0f;
        }

        float restitution = (float) physSection.getDouble("restitution", 0.0d);
        if (restitution < 0f) {
            InertiaLogger.warn("Body '" + id + "' has negative restitution: " + restitution + ", clamping to 0");
            restitution = 0f;
        }

        float linearDamping = (float) physSection.getDouble("linear-damping", 0.05d);
        float angularDamping = (float) physSection.getDouble("angular-damping", 0.05d);

        String motionTypeName = physSection.getString("motion-type", "Dynamic");
        EMotionType motionType;
        try {
            motionType = EMotionType.valueOf(motionTypeName);
        } catch (IllegalArgumentException ex) {
            InertiaLogger.warn("Body '" + id + "' has invalid motion-type: " + motionTypeName
                    + ", falling back to Dynamic");
            motionType = EMotionType.Dynamic;
        }

        int objectLayer = physSection.getInt("object-layer", 0);

        // Примітка: Chain-specific параметри (spacing, joint-offset) наразі ігноруються,
        // оскільки BodyPhysicsSettings не має для них полів у вашому попередньому коді.
        // Якщо вони потрібні, треба розширити клас BodyPhysicsSettings.

        BodyPhysicsSettings physicsSettings = new BodyPhysicsSettings(
                mass,
                friction,
                restitution,
                linearDamping,
                angularDamping,
                motionType,
                objectLayer
        );

        return new BodyDefinition(
                id,
                physicsSettings,
                List.copyOf(shapeLines),
                renderModelId
        );
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