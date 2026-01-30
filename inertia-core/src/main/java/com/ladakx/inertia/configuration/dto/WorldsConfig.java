package com.ladakx.inertia.configuration.dto;

import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.generator.SimulationType;
import com.ladakx.inertia.common.utils.ConfigUtils;
import com.ladakx.inertia.common.serializers.Vec3Serializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WorldsConfig {

    // Мапа: Назва світу (наприклад "world_nether") -> Налаштування
    private final Map<String, WorldProfile> worlds = new HashMap<>();

    public WorldsConfig(FileConfiguration cfg) {
        // Отримуємо всі ключі кореневого рівня (назви світів)
        Set<String> keys = cfg.getKeys(false);

        if (keys.isEmpty()) {
            InertiaLogger.warn("worlds.yml (or config section) is empty! No physics worlds loaded.");
            return;
        }

        for (String key : keys) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section != null) {
                try {
                    WorldProfile profile = parseWorld(section);
                    worlds.put(key, profile);
                } catch (Exception e) {
                    InertiaLogger.error("Failed to parse world settings for '" + key + "': " + e.getMessage());
                }
            }
        }

        InertiaLogger.info("Loaded physics settings for " + worlds.size() + " worlds.");
    }

    private WorldProfile parseWorld(ConfigurationSection section) {
        // --- Main Settings ---
        // Використовуємо Vec3Serializer, як у вашому прикладі
        Vec3 gravity = Vec3Serializer.serialize(section.getString("gravity", "0.0 -17.18 0.0"));
        int tickRate = section.getInt("tick-rate", 20);
        int collisionSteps = section.getInt("collision-steps", 2);

        // Обробка "5000;" та парсинг int
        String maxBodiesRaw = section.getString("max-bodies", "5000").replace(";", "").trim();
        int maxBodies = ConfigUtils.parseIntSafe(maxBodiesRaw, 5000);

        // --- Floor Plane ---
        ConfigurationSection floorSec = section.getConfigurationSection("floor-plane");
        FloorPlaneSettings floorPlane = new FloorPlaneSettings(
                floorSec != null && floorSec.getBoolean("enable", true),
                (float) (floorSec != null ? floorSec.getDouble("y-level", 0.0) : 0.0),
                (float) (floorSec != null ? floorSec.getDouble("y-size", 1.0) : 1.0),
                (float) (floorSec != null ? floorSec.getDouble("friction", 1.0) : 1.0),
                (float) (floorSec != null ? floorSec.getDouble("restitution", 0.025) : 0.025)
        );

        // --- Simulation ---
        ConfigurationSection simSec = section.getConfigurationSection("simulation");
        SimulationType type = SimulationType.NONE;
        boolean simEnable = true;

        if (simSec != null) {
            simEnable = simSec.getBoolean("enable", true);
            try {
                // Безпечний парсинг enum
                type = SimulationType.valueOf(simSec.getString("type", "RAYON").toUpperCase());
            } catch (IllegalArgumentException e) {
                InertiaLogger.warn("Unknown simulation type in world '" + section.getName() + "': " + simSec.getString("type") + ". Defaulting to RAYON.");
            }
        }
        SimulationSettings simulation = new SimulationSettings(simEnable, type);

        // --- Size ---
        ConfigurationSection sizeSec = section.getConfigurationSection("size");
        Vec3 min = new Vec3(-200000, 0, -200000);
        Vec3 max = new Vec3(200000, 1024, 200000);

        if (sizeSec != null) {
            // Додано перевірку на null, щоб уникнути помилок сереалізатора, якщо рядок порожній
            String minStr = sizeSec.getString("min");
            String maxStr = sizeSec.getString("max");
            if (minStr != null) min = Vec3Serializer.serialize(minStr);
            if (maxStr != null) max = Vec3Serializer.serialize(maxStr);
        }
        WorldSizeSettings size = new WorldSizeSettings(min, max);

        return new WorldProfile(gravity, tickRate, collisionSteps, maxBodies, floorPlane, simulation, size);
    }

    /**
     * Отримати налаштування для конкретного світу.
     * @param worldName Назва світу (наприклад, "world")
     * @return Профіль налаштувань або null, якщо не знайдено.
     */
    public WorldProfile getWorldSettings(String worldName) {
        return worlds.get(worldName);
    }

    /**
     * Отримати всі налаштування світів.
     */
    public Map<String, WorldProfile> getAllWorlds() {
        return worlds;
    }

    // --- Nested Data Structures (Records) ---

    public record WorldProfile(
            Vec3 gravity,
            int tickRate,
            int collisionSteps,
            int maxBodies,
            FloorPlaneSettings floorPlane,
            SimulationSettings simulation,
            WorldSizeSettings size
    ) {}

    public record FloorPlaneSettings(boolean enabled, float yLevel, float ySize, float friction, float restitution) {}

    public record SimulationSettings(boolean enabled, SimulationType type) {}

    public record WorldSizeSettings(Vec3 min, Vec3 max) {}
}