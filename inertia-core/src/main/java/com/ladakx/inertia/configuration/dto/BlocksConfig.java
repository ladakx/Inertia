package com.ladakx.inertia.configuration.dto;

import com.github.stephengold.joltjni.AaBox;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.serializers.BoundingSerializer;
import com.ladakx.inertia.physics.world.terrain.profile.PhysicalProfile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public final class BlocksConfig {

    private static final float DEFAULT_DENSITY = 1.0f;
    private static final float DEFAULT_FRICTION = 0.6f;
    private static final float DEFAULT_RESTITUTION = 0.0f;

    private final Map<Material, PhysicalProfile> profiles;

    public BlocksConfig(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        this.profiles = Collections.unmodifiableMap(parse(config));
    }

    private Map<Material, PhysicalProfile> parse(FileConfiguration config) {
        Map<String, PhysicalProfile> materials = parseMaterials(config.getConfigurationSection("materials"));
        Map<Material, PhysicalProfile> result = new LinkedHashMap<>();

        ConfigurationSection blocksSec = config.getConfigurationSection("blocks");
        if (blocksSec == null) {
            return result;
        }

        for (String blockKey : blocksSec.getKeys(false)) {
            ConfigurationSection section = blocksSec.getConfigurationSection(blockKey);
            if (section == null) continue;

            Material material = Material.matchMaterial(blockKey);
            if (material == null) {
                InertiaLogger.warn("Unknown block material in blocks.yml: " + blockKey);
                continue;
            }

            String profileKey = readProfileKey(section);
            PhysicalProfile baseProfile = profileKey != null ? materials.get(profileKey) : null;
            if (baseProfile == null) {
                baseProfile = new PhysicalProfile(profileKey != null ? profileKey : "default", DEFAULT_DENSITY,
                        DEFAULT_FRICTION, DEFAULT_RESTITUTION, List.of());
            }

            Float density = section.contains("density") ? (float) section.getDouble("density") : null;
            Float friction = section.contains("friction") ? (float) section.getDouble("friction") : null;
            Float restitution = section.contains("restitution") ? (float) section.getDouble("restitution") : null;

            PhysicalProfile merged = baseProfile.withOverrides(density, friction, restitution);

            List<AaBox> boxes = List.of();
            if (section.isList("bounding_box")) {
                List<String> rawBoxes = section.getStringList("bounding_box");
                boxes = BoundingSerializer.parseListFromStrings(rawBoxes);
            }

            result.put(material, merged.withBoundingBoxes(boxes));
        }

        return result;
    }

    private Map<String, PhysicalProfile> parseMaterials(ConfigurationSection materialsSec) {
        Map<String, PhysicalProfile> result = new LinkedHashMap<>();
        if (materialsSec == null) return result;

        for (String key : materialsSec.getKeys(false)) {
            ConfigurationSection section = materialsSec.getConfigurationSection(key);
            if (section == null) continue;

            float density = (float) section.getDouble("density", DEFAULT_DENSITY);
            float friction = (float) section.getDouble("friction", DEFAULT_FRICTION);
            float restitution = (float) section.getDouble("restitution", DEFAULT_RESTITUTION);

            result.put(key, new PhysicalProfile(key, density, friction, restitution, List.of()));
        }

        return result;
    }

    private String readProfileKey(ConfigurationSection section) {
        if (section.isString("material")) {
            return section.getString("material");
        }
        if (section.isString("profile")) {
            return section.getString("profile");
        }
        return null;
    }

    public Optional<PhysicalProfile> find(Material material) {
        return Optional.ofNullable(profiles.get(material));
    }

    public PhysicalProfile require(Material material) {
        PhysicalProfile profile = profiles.get(material);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown physical profile for material: " + material);
        }
        return profile;
    }

    public Map<Material, PhysicalProfile> all() {
        return profiles;
    }
}
