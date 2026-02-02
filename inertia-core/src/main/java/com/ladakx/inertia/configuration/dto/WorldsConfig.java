package com.ladakx.inertia.configuration.dto;

import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.utils.ConfigUtils;
import com.ladakx.inertia.common.serializers.Vec3Serializer;
import com.ladakx.inertia.physics.world.terrain.SimulationType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WorldsConfig {

    private final Map<String, WorldProfile> worlds = new HashMap<>();

    public WorldsConfig(FileConfiguration cfg) {
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
        Vec3 gravity = Vec3Serializer.serialize(section.getString("gravity", "0.0 -17.18 0.0"));
        int tickRate = section.getInt("tick-rate", 20);
        int collisionSteps = section.getInt("collision-steps", 4);

        // --- Performance ---
        ConfigurationSection perfSec = section.getConfigurationSection("performance");
        int maxBodiesDefault = section.getInt("max-bodies", 65536);

        int maxBodies = perfSec != null ? perfSec.getInt("max-bodies", maxBodiesDefault) : maxBodiesDefault;
        int numBodyMutexes = perfSec != null ? perfSec.getInt("num-body-mutexes", 0) : 0;
        int maxBodyPairs = perfSec != null ? perfSec.getInt("max-body-pairs", 65536) : 65536;
        int maxContactConstraints = perfSec != null ? perfSec.getInt("max-contact-constraints", 10240) : 10240;
        int tempAllocSize = perfSec != null ? perfSec.getInt("temp-allocator-size", 10 * 1024 * 1024) : 10 * 1024 * 1024;

        PerformanceSettings perfSettings = new PerformanceSettings(maxBodies, numBodyMutexes, maxBodyPairs, maxContactConstraints, tempAllocSize);

        // --- Solver ---
        ConfigurationSection solvSec = section.getConfigurationSection("solver");

        // Базовые
        int velSteps = solvSec != null ? solvSec.getInt("velocity-iterations", 10) : 10;
        int posSteps = solvSec != null ? solvSec.getInt("position-iterations", 2) : 2;
        float baumgarte = solvSec != null ? (float) solvSec.getDouble("baumgarte-stabilization", 0.2) : 0.2f;

        // Расширенные (с дефолтными значениями из Jolt)
        float specContactDist = solvSec != null ? (float) solvSec.getDouble("speculative-contact-distance", 0.02) : 0.02f;
        float penSlop = solvSec != null ? (float) solvSec.getDouble("penetration-slop", 0.02) : 0.02f;
        float linCastThresh = solvSec != null ? (float) solvSec.getDouble("linear-cast-threshold", 0.75) : 0.75f;
        float linCastMaxPen = solvSec != null ? (float) solvSec.getDouble("linear-cast-max-penetration", 0.25) : 0.25f;
        float manifoldTol = solvSec != null ? (float) solvSec.getDouble("manifold-tolerance", 1.0e-3) : 1.0e-3f;
        float maxPenDist = solvSec != null ? (float) solvSec.getDouble("max-penetration-distance", 0.2) : 0.2f;
        boolean warmStart = solvSec == null || solvSec.getBoolean("constraint-warm-start", true);
        boolean useCache = solvSec == null || solvSec.getBoolean("use-body-pair-contact-cache", true);
        boolean splitIslands = solvSec == null || solvSec.getBoolean("use-large-island-splitter", true);
        boolean allowSleep = solvSec == null || solvSec.getBoolean("allow-sleeping", true);
        boolean deterministic = solvSec == null || solvSec.getBoolean("deterministic-simulation", true);

        SolverSettings solverSettings = new SolverSettings(
                velSteps, posSteps, baumgarte,
                specContactDist, penSlop, linCastThresh, linCastMaxPen,
                manifoldTol, maxPenDist,
                warmStart, useCache, splitIslands, allowSleep, deterministic
        );

        // --- Sleeping ---
        ConfigurationSection sleepSec = section.getConfigurationSection("sleeping");
        float sleepThreshold = sleepSec != null ? (float) sleepSec.getDouble("point-velocity-threshold", 0.03) : 0.03f;
        float timeBeforeSleep = sleepSec != null ? (float) sleepSec.getDouble("time-before-sleep", 0.5) : 0.5f;
        SleepSettings sleepSettings = new SleepSettings(sleepThreshold, timeBeforeSleep);

        // --- Simulation ---
        ConfigurationSection simSec = section.getConfigurationSection("simulation");
        boolean simEnable = false;
        SimulationType simType = SimulationType.NONE;
        FloorPlaneSettings floorSettings = null;

        if (simSec != null) {
            simEnable = simSec.getBoolean("enable", false);
            try {
                simType = SimulationType.valueOf(simSec.getString("type", "NONE").toUpperCase());
            } catch (IllegalArgumentException e) {
                InertiaLogger.warn("Unknown simulation type: " + simSec.getString("type"));
            }

            if (simSec.contains("floor-plane")) {
                ConfigurationSection floorSec = simSec.getConfigurationSection("floor-plane");
                if (floorSec != null) {
                    float yLevel = (float) floorSec.getDouble("y-level", 0.0);
                    float ySize = (float) floorSec.getDouble("y-size", 1.0);
                    float friction = (float) floorSec.getDouble("friction", 1.0);
                    float restitution = (float) floorSec.getDouble("restitution", 0.0);

                    ConfigurationSection fSizeSec = floorSec.getConfigurationSection("size");
                    Vec3 fOrigin = new Vec3(0,0,0);
                    float minX = -5000000f, minZ = -5000000f;
                    float maxX = 5000000f, maxZ = 5000000f;

                    if (fSizeSec != null) {
                        fOrigin = Vec3Serializer.serialize(fSizeSec.getString("origin", "0 0 0"));
                        String minStr = fSizeSec.getString("min");
                        if (minStr != null) {
                            String[] parts = minStr.split("\\s+");
                            if (parts.length >= 2) {
                                minX = Float.parseFloat(parts[0]);
                                minZ = Float.parseFloat(parts[1]);
                            }
                        }
                        String maxStr = fSizeSec.getString("max");
                        if (maxStr != null) {
                            String[] parts = maxStr.split("\\s+");
                            if (parts.length >= 2) {
                                maxX = Float.parseFloat(parts[0]);
                                maxZ = Float.parseFloat(parts[1]);
                            }
                        }
                    }
                    floorSettings = new FloorPlaneSettings(yLevel, ySize, friction, restitution,
                            new FloorBounds(fOrigin, minX, minZ, maxX, maxZ));
                }
            }
        }

        if (floorSettings == null) {
            floorSettings = new FloorPlaneSettings(0, 1, 1, 0, new FloorBounds(new Vec3(0,0,0), -100, -100, 100, 100));
        }

        SimulationSettings simulation = new SimulationSettings(simEnable, simType, floorSettings);

        // --- World Size ---
        ConfigurationSection sizeSec = section.getConfigurationSection("size");
        Vec3 origin = new Vec3(0, 0, 0);
        Vec3 min = new Vec3(-5000000, 0, -5000000);
        Vec3 max = new Vec3(5000000, 1024, 5000000);

        if (sizeSec != null) {
            origin = Vec3Serializer.serialize(sizeSec.getString("origin", "0 0 0"));
            String minStr = sizeSec.getString("min");
            String maxStr = sizeSec.getString("max");
            if (minStr != null) min = Vec3Serializer.serialize(minStr);
            if (maxStr != null) max = Vec3Serializer.serialize(maxStr);
        }

        WorldSizeSettings size = new WorldSizeSettings(origin, min, max);

        return new WorldProfile(gravity, tickRate, collisionSteps, solverSettings, perfSettings, sleepSettings, simulation, size);
    }

    public WorldProfile getWorldSettings(String worldName) {
        return worlds.get(worldName);
    }

    public Map<String, WorldProfile> getAllWorlds() {
        return worlds;
    }

    public record WorldProfile(
            Vec3 gravity,
            int tickRate,
            int collisionSteps,
            SolverSettings solver,
            PerformanceSettings performance,
            SleepSettings sleeping,
            SimulationSettings simulation,
            WorldSizeSettings size
    ) {}

    public record SolverSettings(
            int velocityIterations,
            int positionIterations,
            float baumgarte,
            float speculativeContactDistance,
            float penetrationSlop,
            float linearCastThreshold,
            float linearCastMaxPenetration,
            float manifoldTolerance,
            float maxPenetrationDistance,
            boolean constraintWarmStart,
            boolean useBodyPairContactCache,
            boolean useLargeIslandSplitter,
            boolean allowSleeping,
            boolean deterministicSimulation
    ) {}

    public record PerformanceSettings(int maxBodies, int numBodyMutexes, int maxBodyPairs, int maxContactConstraints, int tempAllocatorSize) {}

    public record SleepSettings(float pointVelocityThreshold, float timeBeforeSleep) {}

    public record SimulationSettings(boolean enabled, SimulationType type, FloorPlaneSettings floorPlane) {}

    public record FloorPlaneSettings(float yLevel, float ySize, float friction, float restitution, FloorBounds bounds) {}

    public record FloorBounds(Vec3 origin, float minX, float minZ, float maxX, float maxZ) {}

    public record WorldSizeSettings(Vec3 origin, Vec3 min, Vec3 max) {}
}