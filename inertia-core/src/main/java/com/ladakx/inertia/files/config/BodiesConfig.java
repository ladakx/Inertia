package com.ladakx.inertia.files.config;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.physics.config.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Парсер конфігурації bodies.yml.
 * Підтримує блоки, ланцюги та Ragdoll структури.
 */
public final class BodiesConfig {

    private final Map<String, BodyDefinition> bodies;

    public BodiesConfig(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        this.bodies = Collections.unmodifiableMap(parse(config));
    }

    private Map<String, BodyDefinition> parse(FileConfiguration config) {
        Map<String, BodyDefinition> result = new LinkedHashMap<>();

        // 1. Blocks Parsing
        parseCategory(config, "blocks", result, this::parseBlock);

        // 2. Chains Parsing
        parseCategory(config, "chains", result, this::parseChain);

        // 3. Ragdolls Parsing
        parseCategory(config, "ragdolls", result, this::parseRagdoll);

        InertiaLogger.info("Loaded " + result.size() + " body definitions.");
        return result;
    }

    @FunctionalInterface
    private interface BodyParser {
        BodyDefinition parse(String id, ConfigurationSection section);
    }

    private void parseCategory(FileConfiguration config, String categoryName, Map<String, BodyDefinition> result, BodyParser parser) {
        ConfigurationSection categorySection = config.getConfigurationSection(categoryName);
        if (categorySection == null) return;

        for (String key : categorySection.getKeys(false)) {
            ConfigurationSection bodySection = categorySection.getConfigurationSection(key);
            if (bodySection == null) continue;

            // Використовуємо category.key як ID для унікальності
            String fullId = categoryName + "." + key; 

            try {
                BodyDefinition def = parser.parse(fullId, bodySection);
                if (result.put(fullId, def) != null) {
                    InertiaLogger.warn("Duplicate body id '" + fullId + "' in bodies.yml, overriding.");
                }
            } catch (Exception e) {
                InertiaLogger.error("Failed to parse body '" + fullId + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // --- Specific Parsers ---

    private BlockBodyDefinition parseBlock(String id, ConfigurationSection section) {
        String renderModel = section.getString("render.model", id);
        ConfigurationSection physSection = section.getConfigurationSection("physics");
        
        BodyPhysicsSettings physicsSettings = BodyPhysicsSettings.fromConfig(physSection, id);
        List<String> shapes = physSection != null ? physSection.getStringList("shape") : Collections.emptyList();

        if (shapes.isEmpty()) {
            InertiaLogger.warn("Block '" + id + "' has no physics shapes defined.");
        }

        return new BlockBodyDefinition(id, physicsSettings, shapes, renderModel);
    }

    private ChainBodyDefinition parseChain(String id, ConfigurationSection section) {
        // Базова логіка блоку
        String renderModel = section.getString("render.model", id);
        ConfigurationSection physSection = section.getConfigurationSection("physics");
        BodyPhysicsSettings physicsSettings = BodyPhysicsSettings.fromConfig(physSection, id);
        List<String> shapes = physSection != null ? physSection.getStringList("shape") : Collections.emptyList();

        // Специфіка ланцюга
        ConfigurationSection chainSec = section.getConfigurationSection("chain");
        double offset = chainSec != null ? chainSec.getDouble("joint-offset", 0.5) : 0.5;
        double spacing = chainSec != null ? chainSec.getDouble("spacing", 1.0) : 1.0;

        return new ChainBodyDefinition(
                id, 
                physicsSettings, 
                shapes, 
                renderModel, 
                new ChainBodyDefinition.ChainSettings(offset, spacing)
        );
    }

    private RagdollDefinition parseRagdoll(String id, ConfigurationSection section) {
        // 1. Render Map (Parts -> Models)
        Map<String, String> renderModels = new HashMap<>();
        ConfigurationSection renderSec = section.getConfigurationSection("render");
        if (renderSec != null) {
            for (String part : renderSec.getKeys(false)) {
                String model = renderSec.getString(part + ".model");
                if (model != null) renderModels.put(part, model);
            }
        }

        // 2. Joints Settings
        ConfigurationSection jointsSec = section.getConfigurationSection("joints");
        double armOffsetDiv = jointsSec != null ? jointsSec.getDouble("arm-offset-divisor", 1.0) : 1.0;
        double legOffsetX = jointsSec != null ? jointsSec.getDouble("leg-offset-x", 0.0) : 0.0;
        List<String> fixedAxes = jointsSec != null ? jointsSec.getStringList("fixed-axes") : Collections.emptyList();
        
        RagdollDefinition.RagdollJointSettings jointSettings = new RagdollDefinition.RagdollJointSettings(
                armOffsetDiv, legOffsetX, fixedAxes
        );

        // 3. Physics Settings (Complex)
        ConfigurationSection physSec = section.getConfigurationSection("physics");
        
        // Base physics (friction, restitution, etc.)
        // Note: Ragdolls might not have shapes list in root of physics, but inside 'shapes' section
        BodyPhysicsSettings baseSettings = BodyPhysicsSettings.fromConfig(physSec, id);
        
        // Ragdoll shapes map
        Map<String, List<String>> shapesMap = new HashMap<>();
        if (physSec != null && physSec.isConfigurationSection("shapes")) {
            ConfigurationSection shapesSec = physSec.getConfigurationSection("shapes");
            for (String partKey : shapesSec.getKeys(false)) {
                List<String> partShapes = shapesSec.getStringList(partKey);
                shapesMap.put(partKey, partShapes);
            }
        }
        
        // Mass is explicitly in root physics
        float mass = physSec != null ? (float) physSec.getDouble("mass", 75.0) : 75.0f;

        RagdollDefinition.RagdollPhysicsSettings ragPhysics = new RagdollDefinition.RagdollPhysicsSettings(
                mass, baseSettings, shapesMap
        );

        return new RagdollDefinition(id, renderModels, jointSettings, ragPhysics);
    }

    // --- Accessors ---

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

    /**
     * Повертає всі тіла.
     * Використовуйте instanceof для перевірки конкретного типу (Block/Chain/Ragdoll).
     */
    public Collection<BodyDefinition> all() {
        return bodies.values();
    }
}