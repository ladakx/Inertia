package com.ladakx.inertia.configuration.dto;

import com.github.stephengold.joltjni.enumerate.EAxis;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.body.config.*;
import com.ladakx.inertia.common.serializers.Vec3Serializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.*;

public final class BodiesConfig {

    private final Map<String, BodyDefinition> bodies;

    public BodiesConfig(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        this.bodies = Collections.unmodifiableMap(parse(config));
    }

    private Map<String, BodyDefinition> parse(FileConfiguration config) {
        Map<String, BodyDefinition> result = new LinkedHashMap<>();
        parseCategory(config, "blocks", result, this::parseBlock);
        parseCategory(config, "chains", result, this::parseChain);
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
            String fullId = categoryName + "." + key;
            try {
                BodyDefinition def = parser.parse(fullId, bodySection);
                result.put(fullId, def);
            } catch (Exception e) {
                InertiaLogger.error("Failed to parse body '" + fullId + "' in category '" + categoryName + "'", e);
            }
        }
    }

    /**
     * Helper method to safely retrieve the shape definition.
     * It handles both List<String> and single String formats in YAML.
     */
    private List<String> readShapes(ConfigurationSection physSection) {
        if (physSection == null || !physSection.contains("shape")) {
            return Collections.emptyList();
        }

        if (physSection.isList("shape")) {
            return physSection.getStringList("shape");
        } else if (physSection.isString("shape")) {
            String singleLine = physSection.getString("shape");
            if (singleLine != null && !singleLine.isBlank()) {
                return List.of(singleLine);
            }
        }

        return Collections.emptyList();
    }

    // --- Block Parser ---
    private BlockBodyDefinition parseBlock(String id, ConfigurationSection section) {
        String renderModel = section.getString("render.model", id);
        ConfigurationSection physSection = section.getConfigurationSection("physics");
        BodyPhysicsSettings physicsSettings = BodyPhysicsSettings.fromConfig(physSection, id);

        // Use helper to read shapes
        List<String> shapes = readShapes(physSection);

        return new BlockBodyDefinition(id, physicsSettings, shapes, renderModel);
    }

    private ChainBodyDefinition parseChain(String id, ConfigurationSection section) {
        String renderModel = section.getString("render.model", id);
        ConfigurationSection physSection = section.getConfigurationSection("physics");
        BodyPhysicsSettings physicsSettings = BodyPhysicsSettings.fromConfig(physSection, id);

        List<String> shapes = readShapes(physSection);

        // 1. Creation
        ConfigurationSection creationSec = section.getConfigurationSection("creation");
        double offset = creationSec != null ? creationSec.getDouble("joint-offset", 0.5) : 0.5;
        double spacing = creationSec != null ? creationSec.getDouble("spacing", 1.0) : 1.0;
        ChainBodyDefinition.ChainCreationSettings creation = new ChainBodyDefinition.ChainCreationSettings(offset, spacing);

        // 2. Stabilization
        ConfigurationSection stabSec = section.getConfigurationSection("stabilization");
        int posIter = stabSec != null ? stabSec.getInt("position-iterations", 2) : 2;
        int velIter = stabSec != null ? stabSec.getInt("velocity-iterations", 2) : 2;
        ChainBodyDefinition.ChainStabilizationSettings stabilization = new ChainBodyDefinition.ChainStabilizationSettings(posIter, velIter);

        // 3. Limits
        ConfigurationSection limitSec = section.getConfigurationSection("limits.angular-movement");
        float swingAngle = limitSec != null ? (float) limitSec.getDouble("swing-limit-angle", 45.0) : 45.0f;
        String twistModeStr = limitSec != null ? limitSec.getString("twist-mode", "FREE") : "FREE";
        ChainBodyDefinition.TwistMode twistMode;
        try {
            twistMode = ChainBodyDefinition.TwistMode.valueOf(twistModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            InertiaLogger.warn("Invalid twist-mode in chain '" + id + "': " + twistModeStr + ". Defaulting to FREE.");
            twistMode = ChainBodyDefinition.TwistMode.FREE;
        }
        ChainBodyDefinition.ChainLimitSettings limits = new ChainBodyDefinition.ChainLimitSettings(swingAngle, twistMode);

        // 4. Adaptive Physics (NEW)
        ConfigurationSection adaptSec = section.getConfigurationSection("adaptive");
        ChainBodyDefinition.AdaptiveSettings adaptive;
        if (adaptSec != null && adaptSec.getBoolean("enable", false)) {
            adaptive = new ChainBodyDefinition.AdaptiveSettings(
                    true,
                    adaptSec.getInt("min-length", 10),
                    adaptSec.getInt("max-length", 100),
                    (float) adaptSec.getDouble("gravity.short", 1.0),
                    (float) adaptSec.getDouble("gravity.long", 0.1),
                    adaptSec.getInt("iterations.short", 4),
                    adaptSec.getInt("iterations.long", 32)
            );
        } else {
            adaptive = new ChainBodyDefinition.AdaptiveSettings(false, 0, 0, 0f, 0f, 0, 0);
        }

        return new ChainBodyDefinition(id, physicsSettings, shapes, renderModel, creation, stabilization, limits, adaptive);
    }

    // --- Ragdoll Parser ---
    private RagdollDefinition parseRagdoll(String id, ConfigurationSection section) {
        Map<String, RagdollDefinition.RagdollPartDefinition> parts = new HashMap<>();
        ConfigurationSection partsSection = section.getConfigurationSection("parts");

        if (partsSection == null) {
            throw new IllegalArgumentException("Ragdoll '" + id + "' missing 'parts' section.");
        }

        for (String partKey : partsSection.getKeys(false)) {
            ConfigurationSection partSec = partsSection.getConfigurationSection(partKey);
            if (partSec == null) continue;

            String renderModel = partSec.getString("render-model");
            float mass = (float) partSec.getDouble("mass", 10.0);
            String sizeStr = partSec.getString("size", "0.5 0.5 0.5");
            Vector size = Vec3Serializer.toBukkit(Vec3Serializer.serialize(sizeStr));
            String shapeStr = partSec.getString("shape");
            String parentName = partSec.getString("parent");
            boolean collideWithParent = partSec.getBoolean("collide-with-parent", true);

            // --- Parsing Physics Settings ---
            ConfigurationSection physSec = partSec.getConfigurationSection("physics");
            // Default values that worked well in testing
            float linDamp = 0.05f;
            float angDamp = 0.05f;
            float friction = 0.6f;
            float restitution = 0.0f;
            float gravityFactor = 1.0f;

            if (physSec != null) {
                linDamp = (float) physSec.getDouble("linear-damping", linDamp);
                angDamp = (float) physSec.getDouble("angular-damping", angDamp);
                friction = (float) physSec.getDouble("friction", friction);
                restitution = (float) physSec.getDouble("restitution", restitution);
                gravityFactor = (float) physSec.getDouble("gravity-factor", gravityFactor);
            }
            RagdollDefinition.PartPhysicsSettings physSettings = new RagdollDefinition.PartPhysicsSettings(linDamp, angDamp, friction, restitution, gravityFactor);

            // --- Parsing Joint Settings ---
            RagdollDefinition.JointSettings joint = null;
            if (parentName != null) {
                ConfigurationSection jointSec = partSec.getConfigurationSection("joint");
                Vector pivotParent = new Vector();
                Vector pivotChild = new Vector();
                List<String> fixedAxes = List.of("TranslationX", "TranslationY", "TranslationZ");
                Map<EAxis, RagdollDefinition.RotationLimit> limits = new HashMap<>();

                if (jointSec != null) {
                    pivotParent = Vec3Serializer.toBukkit(Vec3Serializer.serialize(jointSec.getString("parent-pivot", "0 0 0")));
                    pivotChild = Vec3Serializer.toBukkit(Vec3Serializer.serialize(jointSec.getString("child-pivot", "0 0 0")));

                    if (jointSec.contains("fixed-axes")) {
                        fixedAxes = jointSec.getStringList("fixed-axes");
                    }

                    // Limits parsing
                    ConfigurationSection limitSec = jointSec.getConfigurationSection("limits");
                    if (limitSec != null) {
                        for (String axisKey : limitSec.getKeys(false)) {
                            try {
                                // Map "x", "rotation-x", "rotationx" -> RotationX
                                String cleanKey = axisKey.toLowerCase().replace("-", "").replace("_", "");
                                if (!cleanKey.startsWith("rotation")) cleanKey = "rotation" + cleanKey;

                                EAxis axis = EAxis.valueOf(cleanKey.replace("rotationx", "RotationX").replace("rotationy", "RotationY").replace("rotationz", "RotationZ"));

                                String val = limitSec.getString(axisKey);
                                if (val != null) {
                                    String[] minMax = val.trim().split("\\s+");
                                    if (minMax.length == 2) {
                                        float minDeg = Float.parseFloat(minMax[0]);
                                        float maxDeg = Float.parseFloat(minMax[1]);
                                        limits.put(axis, new RagdollDefinition.RotationLimit((float) Math.toRadians(minDeg), (float) Math.toRadians(maxDeg)));
                                    }
                                }
                            } catch (Exception e) {
                                InertiaLogger.warn("Invalid limit key '" + axisKey + "' in ragdoll '" + id + "': " + e.getMessage());
                            }
                        }
                    }
                }
                joint = new RagdollDefinition.JointSettings(pivotParent, pivotChild, fixedAxes, limits);
            }

            parts.put(partKey, new RagdollDefinition.RagdollPartDefinition(
                    renderModel, mass, size, shapeStr, parentName, collideWithParent, joint, physSettings
            ));
        }

        return new RagdollDefinition(id, parts);
    }

    public BodyDefinition require(String id) {
        BodyDefinition def = bodies.get(id);
        if (def == null) throw new IllegalArgumentException("Unknown body id: " + id);
        return def;
    }

    public Collection<BodyDefinition> all() { return bodies.values(); }
}